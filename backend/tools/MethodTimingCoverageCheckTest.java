import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.nio.file.Files;

/** Regression fixtures for rejecting copied timing labels in the maintenance coverage gate. */
public final class MethodTimingCoverageCheckTest {
    /** Verifies declaration-derived labels, including overloads, generics and callbacks. */
    public static void main(String[] args) throws Exception {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        expect("class Owner { void run(long id) {} }", "Owner.class", "run(long)", true);
        expect("class Owner { void run(long id) {} }", "Other.class", "run(long)", false);
        expect("class Owner { void run(long id) {} }", "Owner.class", "copied(long)", false);
        expect("class Owner { void run(long id) {} }", "Owner.class", "run(String)", false);
        expect(
                "class Owner { void run(java.util.Map<String, Object> input, String... values) {}"
                        + " }",
                "Owner.class",
                "run(java.util.Map<String,Object>,String...)",
                true);
        expect("class Outer { class Inner { void run() {} } }", "Outer.Inner.class", "run()", true);
        expect(
                "class Outer { Runnable r = new Runnable() { public void run() {} }; }",
                "Outer.class",
                "field[r]/anonymous[1]/run()",
                true);
        expect("enum Owner { VALUE; void run() {} }", "Owner.class", "run()", true);
        expect(
                "class Owner { void removeMedia() { new Runnable() { public void run() {} }; } }",
                "Owner.class",
                "removeMedia()/anonymous[1]/run()",
                true);
        expect(
                "class Owner { void removeMobile() { new Runnable() { public void run() {} }; } }",
                "Owner.class",
                "removeMedia()/anonymous[1]/run()",
                false);
        expect(
                "class Owner { void run() { new Runnable() { public void run() {} }; new Runnable()"
                        + " { public void run() {} }; } }",
                "Owner.class",
                "run()/anonymous[2]/run()",
                true);
        expect(
                "class Owner { void run() { new Runnable() { public void run() {} }; new Runnable()"
                        + " { public void run() {} }; } }",
                "Owner.class",
                "run()/anonymous[1]/run()",
                false);
        expect(
                "enum Owner { FIRST { public void run() {} }, SECOND { public void run() {} } }",
                "Owner.class",
                "SECOND/run()",
                true);
        expect(
                "enum Owner { FIRST { public void run() {} }, SECOND { public void run() {} } }",
                "Owner.class",
                "FIRST/run()",
                false);
        rejectsPairedCopy();
        rejectsLockDocumentation("<p>Writes {@code OF}.");
        rejectsLockDocumentation("<p>Writes {@code staff_users}, {@code SKIP}.");
        rejectsLockDocumentation("<p>Writes {@code staff_users},\n * {@code OF}.");
        System.out.println("Timing declaration regressions passed (18 fixtures).");
    }

    private static void expect(String source, String owner, String signature, boolean expected) {
        var method = StaticJavaParser.parse(source).findAll(MethodDeclaration.class).getLast();
        var start =
                StaticJavaParser.parseExpression(
                                "MethodTiming.start(" + owner + ", \"" + signature + "\")")
                        .asMethodCallExpr();
        if (MethodTimingCoverageCheck.matchesDeclaration(method, start) != expected)
            throw new AssertionError("Unexpected label validation: " + source + " / " + start);
    }

    private static void rejectsLockDocumentation(String description) throws Exception {
        var directory = Files.createTempDirectory("timing-documentation-regression-");
        var file = directory.resolve("Owner.java");
        try {
            Files.writeString(
                    file,
                    """
                    package fixture;
                    /** Fixture owner. */
                    class Owner {
                        /** Runs the fixture. %s */
                        void run() {
                            final long started = MethodTiming.start(Owner.class, "run()");
                            try {} finally { MethodTiming.finish(started, Owner.class, "run()"); }
                        }
                    }
                    """
                            .formatted(description));
            try {
                MethodTimingCoverageCheck.main(new String[] {directory.toString()});
            } catch (AssertionError rejected) {
                if (!rejected.getMessage().contains("SQL locking keywords")) throw rejected;
                return;
            }
            throw new AssertionError("Locking keywords must not be documented as write targets.");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void rejectsPairedCopy() throws Exception {
        var directory = Files.createTempDirectory("timing-label-regression-");
        var file = directory.resolve("Owner.java");
        try {
            Files.writeString(
                    file,
                    """
package fixture;
/** Fixture owner. */
class Owner {
    /** Runs the fixture. */
    void actual(long id) {
        final long started = MethodTiming.start(Owner.class, "copied(long)");
        try {} finally { MethodTiming.finish(started, Owner.class, "copied(long)"); }
    }
}
""");
            try {
                MethodTimingCoverageCheck.main(new String[] {directory.toString()});
            } catch (AssertionError rejected) {
                if (!rejected.getMessage().contains("inconsistent timing metadata")) throw rejected;
                return;
            }
            throw new AssertionError("Matching copied start/finish labels must fail coverage.");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
