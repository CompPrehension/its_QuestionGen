package its.questions.gen.formulations

import its.model.expressions.operators.CompareWithComparisonOperator.ComparisonOperator
import its.model.nodes.AggregationMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationEnTest {

    /** Подписи вариантов ответа и столбцов сопоставления. */
    @Test
    fun answerCaptions() {
        // Act & Assert.
        assertEquals("True", LocalizationEN.TRUE)
        assertEquals("False", LocalizationEN.FALSE)
        assertEquals("Doesn't matter", LocalizationEN.NOT_IMPORTANT)
        assertEquals("Has no effect", LocalizationEN.NO_EFFECT)
        assertEquals("Yes", LocalizationEN.YES)
        assertEquals("No", LocalizationEN.NO)
        assertEquals("Greater", LocalizationEN.GREATER)
        assertEquals("Less", LocalizationEN.LESS)
        assertEquals("Equal", LocalizationEN.EQUAL)
        assertEquals("Not equal", LocalizationEN.NOT_EQUAL)
        assertEquals("Cannot be determined", LocalizationEN.CANNOT_BE_DETERMINED)
        assertEquals("None can be found.", LocalizationEN.IMPOSSIBLE_TO_FIND)
        assertEquals("None of the above", LocalizationEN.NONE_OF_THE_ABOVE_APPLIES)
        assertEquals("No further discussion is needed", LocalizationEN.NO_FURTHER_DISCUSSION_NEEDED)
    }

    /** Реплики оценки ответа. */
    @Test
    fun verdicts() {
        // Act & Assert.
        assertEquals("Correct.", LocalizationEN.THATS_CORRECT)
        assertEquals("That's incorrect.", LocalizationEN.THATS_INCORRECT)
        assertEquals("That's incorrect, because the vase is fragile.", LocalizationEN.THATS_INCORRECT_BECAUSE("the vase is fragile"))
        assertEquals("In this case, the vase is fragile.", LocalizationEN.IN_THIS_SITUATION("the vase is fragile"))
        assertEquals("Let's figure it out.", LocalizationEN.LETS_FIGURE_IT_OUT)
    }

    /** Тексты вопросов о ходе рассуждения. */
    @Test
    fun reasoningQuestions() {
        // Act & Assert.
        assertEquals("Please match the options to the answers", LocalizationEN.PLEASE_MATCH)
        assertEquals("Why do you think that the vase is fragile?", LocalizationEN.WHY_DO_YOU_THINK_THAT("the vase is fragile"))
        assertEquals("Which is true in this situation?", LocalizationEN.WHICH_IS_TRUE_HERE)
        assertEquals("Is it true that the vase is fragile?", LocalizationEN.IS_IT_TRUE_THAT("the vase is fragile"))
        assertEquals("Why is it that the vase is fragile?", LocalizationEN.WHY_IS_IT_THAT("the vase is fragile"))
        assertEquals("What is the first step to determine if the vase is fragile?", LocalizationEN.DEFAULT_REASONING_START_QUESTION("the vase is fragile"))
        assertEquals("What is the next reasoning step in this case?", LocalizationEN.DEFAULT_NEXT_STEP_QUESTION)
        assertEquals("What do you want to discuss further?", LocalizationEN.WHAT_DO_YOU_WANT_TO_DISCUSS_FURTHER)
    }

    /** Варианты и итоги шагов рассуждения. */
    @Test
    fun reasoningSteps() {
        // Act & Assert.
        assertEquals("We can conclude that the vase is fragile.", LocalizationEN.WE_CAN_CONCLUDE_THAT("the vase is fragile"))
        assertEquals("So, we've discussed why the vase is fragile.", LocalizationEN.SO_WEVE_DISCUSSED_WHY("the vase is fragile"))
        assertEquals("We have already seen that the vase is fragile.", LocalizationEN.WE_ALREADY_DISCUSSED_THAT("the vase is fragile"))
        assertEquals("We need to check if the vase is fragile", LocalizationEN.WE_NEED_TO_CHECK_THAT("the vase is fragile"))
    }

    /** Регрессия: «also fits the criteria» - законченное предложение с заглавной буквы и точкой. */
    @Test
    fun alsoFitsTheCriteriaIsSentence() {
        // Act.
        val text = LocalizationEN.ALSO_FITS_THE_CRITERIA("operator +")

        // Assert.
        assertEquals("Operator + also fits the criteria.", text)
    }

    /** Объяснения ответа на вопрос-сопоставление. */
    @Test
    fun aggregationExplanations() {
        // Act & Assert.
        assertEquals("That's incorrect, because the vase is light.", LocalizationEN.AGGREGATION_INCORRECT_BRANCHES_DESCR("the vase is light"))
        assertEquals(
            "That's incorrect, because you did not consider that the vase is light - this matters in this case.",
            LocalizationEN.AGGREGATION_MISSED_BRANCHES_DESCR_PRIMARY("the vase is light"),
        )
        assertEquals(
            "You also did not consider that the vase is light - this matters in this case.",
            LocalizationEN.AGGREGATION_MISSED_BRANCHES_DESCR_CONCAT("the vase is light"),
        )
    }

    /** Объяснение верного сопоставления - законченное предложение с точкой. */
    @Test
    fun aggregationCorrectExplanationIsSentence() {
        // Act.
        val text = LocalizationEN.AGGREGATION_CORRECT_EXPL("the parcel can be sent", "the vase is light")

        // Assert.
        assertEquals("You've judged the situation correctly, but in this case it means that the parcel can be sent because the vase is light.", text)
    }

    /** Объяснение результата агрегации AND/OR зависит от метода и от того, выполняются ли факторы, и заканчивается точкой. */
    @Test
    fun simAggregationExplanation() {
        // Act & Assert.
        assertEquals(
            "That's incorrect. In order to determine if the parcel can be sent, all of the factors mentioned (the vase is light, the vase is intact) should apply. And in this case they do.",
            LocalizationEN.SIM_AGGREGATION_EXPLANATION(AggregationMethod.AND, "the parcel can be sent", "the vase is light, the vase is intact", true),
        )
        assertEquals(
            "That's incorrect. In order to determine if the parcel can be sent, at least one of the factors mentioned (the vase is light, the vase is intact) should apply. And in this case they don't.",
            LocalizationEN.SIM_AGGREGATION_EXPLANATION(AggregationMethod.OR, "the parcel can be sent", "the vase is light, the vase is intact", false),
        )
    }

    /** Регрессия: объяснение NULL-результата агрегации говорит, что факторы не влияют, без двойного отрицания. */
    @Test
    fun simAggregationNullExplanationHasNoDoubleNegation() {
        // Act.
        val text = LocalizationEN.SIM_AGGREGATION_NULL_EXPLANATION("the vase is light")

        // Assert.
        assertEquals(
            "That's incorrect, because in this case none of the factors mentioned (the vase is light) affect the decision, which means that no definite result can be determined at this stage.",
            text,
        )
    }

    /** Регрессия: вопрос о сравнении значения свойства называет свойство, а не его значение. */
    @Test
    fun propertyComparisonQuestionNamesProperty() {
        // Act.
        val text = LocalizationEN.COMPARE_A_PROPERTY("weight", "vase", "3")

        // Assert.
        assertEquals("Compare the value of weight of vase with value 3", text)
    }

    /** Вопросы о значениях свойств. */
    @Test
    fun propertyQuestions() {
        // Act & Assert.
        assertEquals("Is color of vase equal to red?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_CONSTANT("color", "vase", "red"))
        assertEquals("What is the weight of vase?", LocalizationEN.CHECK_OBJ_PROPERTY_OR_CLASS("weight", "vase"))
        assertEquals("Compare weight of vase with weight of ball", LocalizationEN.COMPARE_A_PROPERTY_WITH_SAME_PROPS_OF_DIFF_OBJ("weight", "vase", "ball"))
    }

    /** Строгие сравнения и равенство с числом. */
    @Test
    fun numericComparisons() {
        // Act & Assert.
        assertEquals("Is weight of vase greater than 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.Greater))
        assertEquals("Is weight of vase less than 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.Less))
        assertEquals("Is weight of vase equal to 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.Equal))
        assertEquals("Is weight of vase equal to 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.NotEqual))
    }

    /** Регрессия: нестрогое сравнение спрашивается через противоположное строгое (ответы при этом инвертируются). */
    @Test
    fun nonStrictComparisonsAskOppositeStrict() {
        // Act & Assert.
        assertEquals("Is weight of vase less than 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.GreaterEqual))
        assertEquals("Is weight of vase greater than 3?", LocalizationEN.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "vase", "3", ComparisonOperator.LessEqual))
        assertEquals("Is weight of vase less than that of ball?", LocalizationEN.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("weight", "vase", "ball", ComparisonOperator.GreaterEqual))
    }

    /** Сравнение свойств двух объектов. */
    @Test
    fun comparisonOfTwoObjectsProperties() {
        // Act & Assert.
        assertEquals("Is weight of vase greater than that of ball?", LocalizationEN.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("weight", "vase", "ball", ComparisonOperator.Greater))
        assertEquals("Is weight of vase equal to that of ball?", LocalizationEN.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("weight", "vase", "ball", ComparisonOperator.Equal))
    }

    /** Вопросы и утверждения о классе объекта выбирают артикль a/an по первой букве класса. */
    @Test
    fun classPhrasesChooseArticle() {
        // Act & Assert.
        assertEquals("Which item is vase?", LocalizationEN.CHECK_OBJECT_CLASS("item", "vase"))
        assertEquals("Is vase an item?", LocalizationEN.IS_OBJ_A_CLASS("item", "vase"))
        assertEquals("Is vase a toy?", LocalizationEN.IS_OBJ_A_CLASS("toy", "vase"))
        assertEquals("vase is an Object", LocalizationEN.CHECK_OBJ_CLASS_EXPL("Object", "vase"))
        assertEquals("vase is a box", LocalizationEN.CHECK_OBJ_CLASS_EXPL("box", "vase"))
        assertEquals("It's greater for vase", LocalizationEN.GREATER_THAN("vase"))
    }

    /** Утверждения для объяснений о свойствах. */
    @Test
    fun assertions() {
        // Act & Assert.
        assertEquals("weight of vase is 5", LocalizationEN.COMPARE_PROP_EXPL("weight", "vase", "5"))
        assertEquals("weight of vase is 5 and weight of ball is 1", LocalizationEN.COMPARE_PROP_OF_DIFF_OBJS_EXPL("weight of vase is 5", "weight of ball is 1"))
    }

    /** Регрессия: утверждение по умолчанию о значении свойства грамматически полное («the value of ... of ...»). */
    @Test
    fun defaultPropertyAssertionHasOf() {
        // Act.
        val text = LocalizationEN.DEFAULT_PROP_ASSERTION("weight", "vase", "5")

        // Assert.
        assertEquals("the value of weight of vase is 5", text)
    }
}
