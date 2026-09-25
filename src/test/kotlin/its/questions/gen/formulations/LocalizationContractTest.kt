package its.questions.gen.formulations

import its.model.expressions.operators.CompareWithComparisonOperator.ComparisonOperator
import its.model.nodes.AggregationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocalizationContractTest {

    private val latin = Regex("[A-Za-z]")
    private val cyrillic = Regex("[А-Яа-яЁё]")

    /**
     * Все фразы локализации; аргументы - нейтральные метки без букв, чтобы в тексте остались только слова самой локализации.
     */
    private fun phrases(l: Localization): Map<String, String> = mapOf(
        "TRUE" to l.TRUE,
        "FALSE" to l.FALSE,
        "NOT_IMPORTANT" to l.NOT_IMPORTANT,
        "NO_EFFECT" to l.NO_EFFECT,
        "YES" to l.YES,
        "NO" to l.NO,
        "GREATER" to l.GREATER,
        "LESS" to l.LESS,
        "EQUAL" to l.EQUAL,
        "NOT_EQUAL" to l.NOT_EQUAL,
        "CANNOT_BE_DETERMINED" to l.CANNOT_BE_DETERMINED,
        "THATS_CORRECT" to l.THATS_CORRECT,
        "THATS_INCORRECT" to l.THATS_INCORRECT,
        "THATS_INCORRECT_BECAUSE" to l.THATS_INCORRECT_BECAUSE("1"),
        "IN_THIS_SITUATION" to l.IN_THIS_SITUATION("1"),
        "PLEASE_MATCH" to l.PLEASE_MATCH,
        "WHY_DO_YOU_THINK_THAT" to l.WHY_DO_YOU_THINK_THAT("1"),
        "LETS_FIGURE_IT_OUT" to l.LETS_FIGURE_IT_OUT,
        "WE_CAN_CONCLUDE_THAT" to l.WE_CAN_CONCLUDE_THAT("1"),
        "SO_WEVE_DISCUSSED_WHY" to l.SO_WEVE_DISCUSSED_WHY("1"),
        "SO_WEVE_DISCUSSED_WHY_ALL" to l.SO_WEVE_DISCUSSED_WHY_ALL(listOf("1", "2", "3")),
        "WE_ALREADY_DISCUSSED_THAT" to l.WE_ALREADY_DISCUSSED_THAT("1"),
        "WHICH_IS_TRUE_HERE" to l.WHICH_IS_TRUE_HERE,
        "NONE_OF_THE_ABOVE_APPLIES" to l.NONE_OF_THE_ABOVE_APPLIES,
        "IS_IT_TRUE_THAT" to l.IS_IT_TRUE_THAT("1"),
        "WHY_IS_IT_THAT" to l.WHY_IS_IT_THAT("1"),
        "WE_NEED_TO_CHECK_THAT" to l.WE_NEED_TO_CHECK_THAT("1"),
        "DEFAULT_REASONING_START_QUESTION" to l.DEFAULT_REASONING_START_QUESTION("1"),
        "DEFAULT_NEXT_STEP_QUESTION" to l.DEFAULT_NEXT_STEP_QUESTION,
        "WHAT_DO_YOU_WANT_TO_DISCUSS_FURTHER" to l.WHAT_DO_YOU_WANT_TO_DISCUSS_FURTHER,
        "NO_FURTHER_DISCUSSION_NEEDED" to l.NO_FURTHER_DISCUSSION_NEEDED,
        "IMPOSSIBLE_TO_FIND" to l.IMPOSSIBLE_TO_FIND,
        "ALSO_FITS_THE_CRITERIA" to l.ALSO_FITS_THE_CRITERIA("1"),
        "AGGREGATION_CORRECT_EXPL" to l.AGGREGATION_CORRECT_EXPL("1", "2"),
        "AGGREGATION_INCORRECT_BRANCHES_DESCR" to l.AGGREGATION_INCORRECT_BRANCHES_DESCR("1"),
        "AGGREGATION_MISSED_BRANCHES_DESCR_PRIMARY" to l.AGGREGATION_MISSED_BRANCHES_DESCR_PRIMARY("1"),
        "AGGREGATION_MISSED_BRANCHES_DESCR_CONCAT" to l.AGGREGATION_MISSED_BRANCHES_DESCR_CONCAT("1"),
        "SIM_AGGREGATION_EXPLANATION AND" to l.SIM_AGGREGATION_EXPLANATION(AggregationMethod.AND, "1", "2", true),
        "SIM_AGGREGATION_EXPLANATION OR" to l.SIM_AGGREGATION_EXPLANATION(AggregationMethod.OR, "1", "2", false),
        "SIM_AGGREGATION_NULL_EXPLANATION" to l.SIM_AGGREGATION_NULL_EXPLANATION("1"),
        "COMPARE_A_PROPERTY_TO_A_CONSTANT" to l.COMPARE_A_PROPERTY_TO_A_CONSTANT("1", "2", "3"),
        "COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST Greater" to l.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("1", "2", "3", ComparisonOperator.Greater),
        "COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST Less" to l.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("1", "2", "3", ComparisonOperator.Less),
        "COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST Equal" to l.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("1", "2", "3", ComparisonOperator.Equal),
        "COMPARE_A_PROPERTY" to l.COMPARE_A_PROPERTY("1", "2", "3"),
        "CHECK_OBJ_PROPERTY_OR_CLASS" to l.CHECK_OBJ_PROPERTY_OR_CLASS("1", "2"),
        "COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ Greater" to l.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("1", "2", "3", ComparisonOperator.Greater),
        "COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ Equal" to l.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("1", "2", "3", ComparisonOperator.Equal),
        "COMPARE_A_PROPERTY_WITH_SAME_PROPS_OF_DIFF_OBJ" to l.COMPARE_A_PROPERTY_WITH_SAME_PROPS_OF_DIFF_OBJ("1", "2", "3"),
        "CHECK_OBJECT_CLASS" to l.CHECK_OBJECT_CLASS("1", "2"),
        "IS_OBJ_A_CLASS" to l.IS_OBJ_A_CLASS("1", "2"),
        "GREATER_THAN" to l.GREATER_THAN("1"),
        "COMPARE_PROP_EXPL" to l.COMPARE_PROP_EXPL("1", "2", "3"),
        "CHECK_OBJ_CLASS_EXPL" to l.CHECK_OBJ_CLASS_EXPL("1", "2"),
        "COMPARE_PROP_OF_DIFF_OBJS_EXPL" to l.COMPARE_PROP_OF_DIFF_OBJS_EXPL("1", "2"),
        "DEFAULT_PROP_ASSERTION" to l.DEFAULT_PROP_ASSERTION("1", "2", "3"),
    )

    /** Зарегистрированы ровно русская и английская локализации под своими кодами. */
    @Test
    fun localizationsAreRegisteredByCode() {
        // Act & Assert.
        assertEquals(setOf("RU", "EN"), Localization.localizations.keys)
        assertSame(LocalizationRU, Localization.getLocalization("RU"))
        assertSame(LocalizationEN, Localization.getLocalization("EN"))
        assertEquals("RU", LocalizationRU.codePrefix)
        assertEquals("EN", LocalizationEN.codePrefix)
    }

    /** В русских фразах нет латиницы. */
    @Test
    fun russianPhrasesHaveNoLatin() {
        // Act.
        val withLatin = phrases(LocalizationRU).filterValues { latin.containsMatchIn(it) }

        // Assert.
        assertEquals(emptyMap(), withLatin)
    }

    /** В английских фразах нет кириллицы. */
    @Test
    fun englishPhrasesHaveNoCyrillic() {
        // Act.
        val withCyrillic = phrases(LocalizationEN).filterValues { cyrillic.containsMatchIn(it) }

        // Assert.
        assertEquals(emptyMap(), withCyrillic)
    }

    /** Каждая фраза подставляет все переданные ей аргументы. */
    @Test
    fun everyPhraseUsesItsArguments() {
        // Arrange.
        val arguments = mapOf(
            "COMPARE_A_PROPERTY_TO_A_CONSTANT" to listOf("1", "2", "3"),
            "COMPARE_A_PROPERTY" to listOf("1", "2", "3"),
            "COMPARE_PROP_EXPL" to listOf("1", "2", "3"),
            "DEFAULT_PROP_ASSERTION" to listOf("1", "2", "3"),
            "COMPARE_A_PROPERTY_WITH_SAME_PROPS_OF_DIFF_OBJ" to listOf("1", "2", "3"),
            "COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ Equal" to listOf("1", "2", "3"),
            "AGGREGATION_CORRECT_EXPL" to listOf("1", "2"),
            "CHECK_OBJECT_CLASS" to listOf("1", "2"),
            "IS_OBJ_A_CLASS" to listOf("1", "2"),
            "CHECK_OBJ_CLASS_EXPL" to listOf("1", "2"),
            "COMPARE_PROP_OF_DIFF_OBJS_EXPL" to listOf("1", "2"),
        )

        // Act.
        val missing = listOf(LocalizationRU, LocalizationEN).flatMap { l ->
            val texts = phrases(l)
            arguments.flatMap { (key, args) -> args.filter { it !in texts[key]!! }.map { "${l.codePrefix} $key: $it" } }
        }

        // Assert.
        assertEquals(emptyList(), missing)
    }

    /** Все фразы переведены: русский и английский тексты различаются. */
    @Test
    fun phrasesDifferBetweenLocales() {
        // Arrange.
        val ru = phrases(LocalizationRU)
        val en = phrases(LocalizationEN)

        // Act.
        val same = ru.keys.filter { ru[it] == en[it] }

        // Assert.
        assertEquals(emptyList(), same)
    }
}
