import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.TryStmt;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Enforces documentation and explicit method timing for future maintained backend Java sources. */
public final class MethodTimingCoverageCheck {
    /**
     * Rejects undocumented declarations or concrete business methods without the timing contract.
     */
    public static void main(String[] args) throws Exception {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        var failures = new ArrayList<String>();
        int methods = 0;
        var diagnosticLabels = new HashMap<String, String>();
        List<Path> sources;
        try (var paths = Files.walk(Path.of(args[0]))) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        for (Path path : sources) {
            var cu = StaticJavaParser.parse(path);
            boolean diagnostics =
                    cu.getPackageDeclaration()
                            .orElseThrow()
                            .getNameAsString()
                            .endsWith(".observability");
            for (var type : cu.findAll(TypeDeclaration.class)) {
                if (type.getJavadocComment().isEmpty())
                    failures.add(path + "#" + type.getNameAsString() + ": missing JavaDoc");
            }
            for (var method : cu.findAll(MethodDeclaration.class)) {
                String key = path + "#" + method.getNameAsString();
                if (method.getJavadocComment().isEmpty()) failures.add(key + ": missing JavaDoc");
                else if (method.getJavadocComment()
                        .orElseThrow()
                        .getContent()
                        .contains("the operation."))
                    failures.add(key + ": replace placeholder JavaDoc with a method contract");
                if (method.getJavadocComment().isPresent()
                        && hasInvalidWriteTarget(
                                method.getJavadocComment().orElseThrow().getContent()))
                    failures.add(key + ": SQL locking keywords are not write targets");
                if (method.getBody().isEmpty() || diagnostics) continue;
                methods++;
                var body = method.getBody().orElseThrow();
                if (body.getStatements().size() != 2
                        || !(body.getStatement(1) instanceof TryStmt wrapped)
                        || wrapped.getFinallyBlock().isEmpty()
                        || !wrapped.getCatchClauses().isEmpty()
                        || !wrapped.getResources().isEmpty()) {
                    failures.add(key + ": expected timing declaration plus try/finally");
                    continue;
                }
                try {
                    var variable =
                            body.getStatement(0)
                                    .asExpressionStmt()
                                    .getExpression()
                                    .asVariableDeclarationExpr()
                                    .getVariable(0);
                    var start = variable.getInitializer().orElseThrow().asMethodCallExpr();
                    var finish =
                            wrapped.getFinallyBlock()
                                    .orElseThrow()
                                    .getStatement(0)
                                    .asExpressionStmt()
                                    .getExpression()
                                    .asMethodCallExpr();
                    if (!isTimingCall(start, "start", 2)
                            || !isTimingCall(finish, "finish", 3)
                            || !finish.getArgument(0).toString().equals(variable.getNameAsString())
                            || !start.getArgument(0).equals(finish.getArgument(1))
                            || !start.getArgument(1).equals(finish.getArgument(2))
                            || !matchesDeclaration(method, start)
                            || wrapped.getFinallyBlock().orElseThrow().getStatements().size()
                                    != 1) {
                        failures.add(key + ": inconsistent timing metadata or cleanup");
                    } else {
                        String label =
                                cu.getPackageDeclaration().orElseThrow().getNameAsString()
                                        + "."
                                        + start.getArgument(0)
                                        + "/"
                                        + start.getArgument(1).asStringLiteralExpr().asString();
                        String previous = diagnosticLabels.putIfAbsent(label, key);
                        if (previous != null)
                            failures.add(
                                    key + ": duplicate diagnostic label also used by " + previous);
                    }
                } catch (RuntimeException invalidWrapper) {
                    failures.add(key + ": invalid timing wrapper");
                }
            }
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println(
                "Documentation and timing coverage passed for "
                        + methods
                        + " explicit business methods.");
    }

    /** Rejects SQL locking keywords anywhere in an explicitly documented write-target list. */
    private static boolean hasInvalidWriteTarget(String content) {
        String prose = content.replaceAll("(?m)^[ \t]*\\*[ \t]?", "");
        var paragraphs =
                java.util.regex.Pattern.compile(
                                "(?s)<p>Writes\\s+(.+?)(?=\\n[ \t]*\\n|<p>|\\n[ \t]*@|$)")
                        .matcher(prose);
        var invalid =
                java.util.regex.Pattern.compile("\\{@code\\s+(OF|SKIP|NOWAIT|SET|LOCKED)\\s*\\}");
        while (paragraphs.find()) {
            if (invalid.matcher(paragraphs.group(1)).find()) return true;
        }
        return false;
    }

    /**
     * Validates labels against the nearest named type and the source parameter signature.
     * Anonymous-class callbacks include enclosing method/field context and a lexical ordinal;
     * enum-constant overrides include the constant name.
     */
    static boolean matchesDeclaration(MethodDeclaration method, MethodCallExpr start) {
        var owners = new ArrayList<String>();
        for (var node = method.getParentNode();
                node.isPresent();
                node = node.orElseThrow().getParentNode()) {
            if (node.orElseThrow() instanceof TypeDeclaration<?> type)
                owners.add(type.getNameAsString());
        }
        Collections.reverse(owners);
        String owner = String.join(".", owners);
        String signature = expectedSignature(method);
        return start.getArguments().size() == 2
                && start.getArgument(0) instanceof ClassExpr classLabel
                && classLabel.getType().toString().equals(owner)
                && start.getArgument(1).isStringLiteralExpr()
                && start.getArgument(1).asStringLiteralExpr().asString().equals(signature);
    }

    /** Adds lexical callback context so distinct anonymous implementations have distinct labels. */
    static String expectedSignature(MethodDeclaration method) {
        for (var node = method.getParentNode();
                node.isPresent();
                node = node.orElseThrow().getParentNode()) {
            if (node.orElseThrow() instanceof TypeDeclaration<?>) break;
            if (node.orElseThrow() instanceof EnumConstantDeclaration constant)
                return constant.getNameAsString() + "/" + sourceSignature(method);
            if (node.orElseThrow() instanceof ObjectCreationExpr anonymous
                    && anonymous.getAnonymousClassBody().isPresent()) {
                var owner = method.findAncestor(TypeDeclaration.class).orElseThrow();
                var instances =
                        owner.findAll(
                                ObjectCreationExpr.class,
                                creation -> creation.getAnonymousClassBody().isPresent());
                int ordinal = 0;
                for (int i = 0; i < instances.size(); i++) {
                    if (instances.get(i) == anonymous) {
                        ordinal = i + 1;
                        break;
                    }
                }
                if (ordinal == 0)
                    throw new IllegalStateException("Anonymous callback has no lexical owner");
                String context =
                        method.findAncestor(MethodDeclaration.class)
                                .map(MethodTimingCoverageCheck::sourceSignature)
                                .orElseGet(
                                        () ->
                                                anonymous
                                                        .findAncestor(VariableDeclarator.class)
                                                        .map(
                                                                variable ->
                                                                        "field["
                                                                                + variable
                                                                                        .getNameAsString()
                                                                                + "]")
                                                        .orElse("initializer"));
                return context + "/anonymous[" + ordinal + "]/" + sourceSignature(method);
            }
        }
        return sourceSignature(method);
    }

    private static String sourceSignature(MethodDeclaration method) {
        return method.getNameAsString()
                + "("
                + method.getParameters().stream()
                        .map(
                                parameter ->
                                        parameter.getType().toString().replaceAll("\\s+", "")
                                                + (parameter.isVarArgs() ? "..." : ""))
                        .collect(Collectors.joining(","))
                + ")";
    }

    private static boolean isTimingCall(MethodCallExpr call, String name, int arguments) {
        return call.getNameAsString().equals(name)
                && call.getArguments().size() == arguments
                && call.getScope().map(Object::toString).orElse("").equals("MethodTiming");
    }
}
