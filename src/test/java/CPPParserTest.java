import org.variantsync.diffdetective.variation.diff.parse.IllFormedAnnotationException;
import org.variantsync.diffdetective.feature.CPPDiffLineFormulaExtractor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

public class CPPParserTest {
    private static record TestCase(String formula, String expected) {}
    private static record ThrowingTestCase(String formula) {}

    private static List<TestCase> testCases() {
        return List.of(
            new TestCase("#if A", "A"),
            new TestCase("#ifdef A", "A"),
            new TestCase("#ifndef A", "!(A)"),
            new TestCase("#elif A", "A"),

            new TestCase("#if !A", "!A"),
            new TestCase("#if A && B", "A&&B"),
            new TestCase("#if A || B", "A||B"),
            new TestCase("#if A && (B || C)", "A&&(B||C)"),

            new TestCase("#if 1 > -42", "1__GT____SUB__42"),
            new TestCase("#if 1 > +42", "1__GT____ADD__42"),
            new TestCase("#if 42 > A", "42__GT__A"),
            new TestCase("#if 42 > ~A", "42__GT____NOT__A"),
            new TestCase("#if A + B > 42", "A__ADD__B__GT__42"),
            new TestCase("#if A << B", "A__LSHIFT__B"),
            new TestCase("#if A ? B : C", "A__THEN__B__ELSE__C"),
            new TestCase("#if A >= B && C > D", "A__GEQ__B&&C__GT__D"),
            new TestCase("#if A * (B + C)", "A__MUL____LB__B__ADD__C__RB__"),
            new TestCase("#if defined(A) && (B * 2) > C", "DEFINED_A&&__LB__B__MUL__2__RB____GT__C"),
            new TestCase("#if(STDC == 1) && (defined(LARGE) || defined(COMPACT))", "__LB__STDC__EQ__1__RB__&&(DEFINED_LARGE||DEFINED_COMPACT)"),
            new TestCase("#if (('Z' - 'A') == 25)", "__LB____LB__'Z'__SUB__'A'__RB____EQ__25__RB__"),
            new TestCase("#if APR_CHARSET_EBCDIC && !(('Z' - 'A') == 25)", "APR_CHARSET_EBCDIC&&!__LB____LB__'Z'__SUB__'A'__RB____EQ__25__RB__"),
            new TestCase("#if __has_include(<nss3/nss.h>)", "__has_include__LB____LT__nss3__DIV__nss.h__GT____RB__"),
            new TestCase("# if ((GNUTLS_VERSION_MAJOR + (GNUTLS_VERSION_MINOR > 0 || GNUTLS_VERSION_PATCH >= 20)) > 3)",
                    "__LB____LB__GNUTLS_VERSION_MAJOR__ADD____LB__GNUTLS_VERSION_MINOR__GT__0__OR__GNUTLS_VERSION_PATCH__GEQ__20__RB____RB____GT__3__RB__"),

            new TestCase("#if A && (B > C)", "A&&(B__GT__C)"),
            new TestCase("#if (A && B) > C", "__LB__A__AND__B__RB____GT__C"),
            new TestCase("#if C == (A || B)", "C__EQ____LB__A__OR__B__RB__"),
            new TestCase("#if ((A && B) > C)", "(__LB__A__AND__B__RB____GT__C)"),
            new TestCase("#if A && ((B + 1) > (C || D))", "A&&(__LB__B__ADD__1__RB____GT____LB__C__OR__D__RB)"),


            new TestCase("#if A // Comment && B", "A"),
            new TestCase("#if A /* Comment */ && B", "A&&B"),
            new TestCase("#if A && B /* Multiline Comment", "A&&B"),

            new TestCase("#if A == B", "A__EQ__B"),
            new TestCase("#if A == 1", "A__EQ__1"),

            new TestCase("#if defined A", "DEFINED_A"),
            new TestCase("#if defined(A)", "DEFINED_A"),
            new TestCase("#if defined (A)", "DEFINED_A"),
            new TestCase("#if defined ( A )", "DEFINED_A"),
            new TestCase("#if (defined A)", "__LB__DEFINED_A__RB__"),
            new TestCase("#if MACRO (A)", "MACRO__LB__A__RB__"),
            new TestCase("#if MACRO (A, B)", "MACRO__LB__A__B__RB__"),
            new TestCase("#if MACRO (A, B + C)", "MACRO__LB__A__B__ADD__C__RB__"),
            new TestCase("#if MACRO (A, B) == 1", "MACRO__LB__A__B__RB____EQ__1"),

            new TestCase("#if ifndef", "ifndef")
        );
    }

    private static List<ThrowingTestCase> throwingTestCases() {
        return List.of(
            // Invalid macro
            new ThrowingTestCase(""),
            new ThrowingTestCase("#"),
            new ThrowingTestCase("ifdef A"),
            new ThrowingTestCase("#error A"),
            new ThrowingTestCase("#iferror A"),

            // Empty formula
            new ThrowingTestCase("#ifdef"),
            new ThrowingTestCase("#ifdef // Comment"),
            new ThrowingTestCase("#ifdef /* Comment */")
        );
    }

    private static List<TestCase> wontfixTestCases() {
        return List.of(
            new TestCase("#if A == '1'", "A__EQ____TICK__1__TICK__"),
            new TestCase("#if A && (B - (C || D))", "A&&(B__MINUS__LB__C__LOR__D__RB__)")
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testCase(TestCase testCase) throws IllFormedAnnotationException {
        assertEquals(
            testCase.expected,
            new CPPDiffLineFormulaExtractor().extractFormula(testCase.formula())
        );
    }

    @ParameterizedTest
    @MethodSource("throwingTestCases")
    public void throwingTestCase(ThrowingTestCase testCase) {
        assertThrows(IllFormedAnnotationException.class, () ->
            new CPPDiffLineFormulaExtractor().extractFormula(testCase.formula)
        );
    }

    @Disabled("WONTFIX")
    @ParameterizedTest
    @MethodSource("wontfixTestCases")
    public void wontfixTestCase(TestCase testCase) throws IllFormedAnnotationException {
        assertEquals(
            testCase.expected,
            new CPPDiffLineFormulaExtractor().extractFormula(testCase.formula())
        );
    }

}
