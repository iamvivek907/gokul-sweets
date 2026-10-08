import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.TryStmt;

import java.nio.file.*;
import java.util.*;

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
        for (Path path :
                Files.walk(Path.of(args[0])).filter(p -> p.toString().endsWith(".java")).toList()) {
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
                            || wrapped.getFinallyBlock().orElseThrow().getStatements().size()
                                    != 1) {
                        failures.add(key + ": inconsistent timing metadata or cleanup");
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

    private static boolean isTimingCall(MethodCallExpr call, String name, int arguments) {
        return call.getNameAsString().equals(name)
                && call.getArguments().size() == arguments
                && call.getScope().map(Object::toString).orElse("").equals("MethodTiming");
    }
}
