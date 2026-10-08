import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Verifies this maintenance change against a supplied Git revision, without executing business
 * code.
 */
public final class BehaviorPreservationCheck {
    private static final String TIMING = "com.gokulsweets.restaurant.observability.MethodTiming";
    private static final Map<String, Expression> CONSTANTS = new HashMap<>();

    /** Compares existing declarations and executable syntax after removing approved additions. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: <repository-root> <base-revision>");
        Path repo = Path.of(args[0]).toAbsolutePath();
        String base = args[1];
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        for (Path path :
                Files.walk(repo.resolve("backend/src/main/java"))
                        .filter(p -> p.getFileName().toString().equals("AppConstant.java"))
                        .toList()) {
            var cu = StaticJavaParser.parse(path);
            String pkg = cu.getPackageDeclaration().orElseThrow().getNameAsString();
            for (var field : cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
                for (var variable : field.getVariables()) {
                    CONSTANTS.put(
                            pkg + ".AppConstant." + variable.getNameAsString(),
                            variable.getInitializer().orElseThrow());
                }
            }
        }
        String tracked = git(repo, "ls-tree", "-r", "--name-only", base, "backend/src");
        int checked = 0, timed = 0;
        for (String name : tracked.lines().filter(s -> s.endsWith(".java")).toList()) {
            var original = StaticJavaParser.parse(git(repo, "show", base + ":" + name));
            var current = StaticJavaParser.parse(repo.resolve(name));
            Set<String> originalImports =
                    new HashSet<>(original.getImports().stream().map(Object::toString).toList());
            for (var imported : current.getImports()) {
                if (!imported.getNameAsString().equals(TIMING)
                        && !originalImports.contains(imported.toString())) {
                    throw new AssertionError("Unexpected import change: " + name);
                }
            }
            if (name.startsWith("backend/src/main/")) {
                var declarations = new ArrayList<>(current.findAll(MethodDeclaration.class));
                Collections.reverse(declarations);
                for (var method : declarations) {
                    if (method.getBody().isEmpty()) continue;
                    BlockStmt body = method.getBody().orElseThrow();
                    if (body.getStatements().size() != 2
                            || !(body.getStatement(1) instanceof TryStmt wrapped)) {
                        throw new AssertionError(
                                "Missing method timing: " + name + "#" + method.getNameAsString());
                    }
                    var declaration =
                            body.getStatement(0)
                                    .asExpressionStmt()
                                    .getExpression()
                                    .asVariableDeclarationExpr();
                    var marker = declaration.getVariable(0);
                    var start = marker.getInitializer().orElseThrow().asMethodCallExpr();
                    var end =
                            wrapped.getFinallyBlock()
                                    .orElseThrow()
                                    .getStatement(0)
                                    .asExpressionStmt()
                                    .getExpression()
                                    .asMethodCallExpr();
                    if (!start.getScope().orElseThrow().toString().equals("MethodTiming")
                            || !start.getNameAsString().equals("start")
                            || !end.getScope().orElseThrow().toString().equals("MethodTiming")
                            || !end.getNameAsString().equals("finish")
                            || !end.getArgument(0).toString().equals(marker.getNameAsString())
                            || (!start.getArgument(0).equals(end.getArgument(1))
                                    || !start.getArgument(1).equals(end.getArgument(2)))
                            || !wrapped.getCatchClauses().isEmpty()
                            || !wrapped.getResources().isEmpty()
                            || wrapped.getFinallyBlock().orElseThrow().getStatements().size()
                                    != 1) {
                        throw new AssertionError("Unexpected timing wrapper: " + name);
                    }
                    method.setBody(wrapped.getTryBlock().clone());
                    timed++;
                }
                String pkg = current.getPackageDeclaration().orElseThrow().getNameAsString();
                for (var expr : new ArrayList<>(current.findAll(FieldAccessExpr.class))) {
                    String key =
                            expr.getScope().toString().equals("AppConstant")
                                    ? pkg + "." + expr
                                    : expr.toString();
                    var value = CONSTANTS.get(key);
                    if (value != null) expr.replace(value.clone());
                }
            }
            normalize(original);
            normalize(current);
            if (!original.equals(current)) {
                Files.writeString(
                        Path.of("/tmp/gokul-preservation-original.java"), original.toString());
                Files.writeString(
                        Path.of("/tmp/gokul-preservation-current.java"), current.toString());
                throw new AssertionError("Executable syntax changed: " + name);
            }
            checked++;
        }
        System.out.println(
                "Preserved executable syntax for "
                        + checked
                        + " existing Java files; validated "
                        + timed
                        + " timing wrappers and "
                        + CONSTANTS.size()
                        + " extracted constant values.");
    }

    private static void normalize(CompilationUnit cu) {
        cu.getImports().clear();
        for (Node node : cu.findAll(Node.class)) {
            node.removeComment();
            for (Comment comment : new ArrayList<>(node.getOrphanComments()))
                node.removeOrphanComment(comment);
        }
        for (var text : new ArrayList<>(cu.findAll(TextBlockLiteralExpr.class)))
            text.replace(new StringLiteralExpr().setString(text.asString()));
        // The formatter may split a string within a left-associative concatenation chain.
        var concatenations = new ArrayList<>(cu.findAll(BinaryExpr.class));
        Collections.reverse(concatenations);
        for (var expression : concatenations) {
            if (expression.getOperator() != BinaryExpr.Operator.PLUS) continue;
            var parts = new ArrayList<Expression>();
            flattenPlus(expression, parts);
            var compact = new ArrayList<Expression>();
            for (var part : parts) {
                if (!compact.isEmpty()
                        && compact.getLast() instanceof StringLiteralExpr previous
                        && part instanceof StringLiteralExpr next) {
                    compact.set(
                            compact.size() - 1,
                            new StringLiteralExpr()
                                    .setString(previous.asString() + next.asString()));
                } else {
                    compact.add(part.clone());
                }
            }
            Expression normalized = compact.getFirst();
            for (int i = 1; i < compact.size(); i++)
                normalized = new BinaryExpr(normalized, compact.get(i), BinaryExpr.Operator.PLUS);
            expression.replace(normalized);
        }
    }

    private static void flattenPlus(Expression expression, List<Expression> parts) {
        if (expression instanceof BinaryExpr binary
                && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            flattenPlus(binary.getLeft(), parts);
            flattenPlus(binary.getRight(), parts);
        } else {
            parts.add(expression);
        }
    }

    private static String git(Path directory, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(args));
        var process =
                new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Git read failed");
        return output;
    }
}
