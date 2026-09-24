package its.questions.gen.formulations

import its.model.expressions.operators.CompareWithComparisonOperator.ComparisonOperator
import its.model.nodes.AggregationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalizationRuTest {

    private val latin = Regex("[A-Za-z]")

    /** Подписи вариантов ответа и столбцов сопоставления. */
    @Test
    fun answerCaptions() {
        // Act & Assert.
        assertEquals("Верно", LocalizationRU.TRUE)
        assertEquals("Неверно", LocalizationRU.FALSE)
        assertEquals("Не имеет значения", LocalizationRU.NOT_IMPORTANT)
        assertEquals("Не влияет", LocalizationRU.NO_EFFECT)
        assertEquals("Да", LocalizationRU.YES)
        assertEquals("Нет", LocalizationRU.NO)
        assertEquals("Больше", LocalizationRU.GREATER)
        assertEquals("Меньше", LocalizationRU.LESS)
        assertEquals("Равно", LocalizationRU.EQUAL)
        assertEquals("Не равно", LocalizationRU.NOT_EQUAL)
        assertEquals("Невозможно определить", LocalizationRU.CANNOT_BE_DETERMINED)
        assertEquals("Невозможно найти.", LocalizationRU.IMPOSSIBLE_TO_FIND)
        assertEquals("Ничто из вышеперечисленного не применимо", LocalizationRU.NONE_OF_THE_ABOVE_APPLIES)
        assertEquals("Подробный разбор не нужен", LocalizationRU.NO_FURTHER_DISCUSSION_NEEDED)
    }

    /** Реплики оценки ответа. */
    @Test
    fun verdicts() {
        // Act & Assert.
        assertEquals("Верно.", LocalizationRU.THATS_CORRECT)
        assertEquals("Это неверно.", LocalizationRU.THATS_INCORRECT)
        assertEquals("Это неверно, поскольку ваза хрупкая.", LocalizationRU.THATS_INCORRECT_BECAUSE("ваза хрупкая"))
        assertEquals("В данной ситуации ваза хрупкая.", LocalizationRU.IN_THIS_SITUATION("ваза хрупкая"))
        assertEquals("Давайте разберемся.", LocalizationRU.LETS_FIGURE_IT_OUT)
    }

    /** Тексты вопросов о ходе рассуждения. */
    @Test
    fun reasoningQuestions() {
        // Act & Assert.
        assertEquals("Пожалуйста, сопоставьте ответы", LocalizationRU.PLEASE_MATCH)
        assertEquals("Почему вы считаете, что ваза хрупкая?", LocalizationRU.WHY_DO_YOU_THINK_THAT("ваза хрупкая"))
        assertEquals("Что из перечисленного применимо в данной ситуации?", LocalizationRU.WHICH_IS_TRUE_HERE)
        assertEquals("Верно ли, что ваза хрупкая?", LocalizationRU.IS_IT_TRUE_THAT("ваза хрупкая"))
        assertEquals("Почему ваза хрупкая?", LocalizationRU.WHY_IS_IT_THAT("ваза хрупкая"))
        assertEquals("С чего надо начать, чтобы проверить, что ваза хрупкая?", LocalizationRU.DEFAULT_REASONING_START_QUESTION("ваза хрупкая"))
        assertEquals("Какой следующий шаг необходим для решения задачи?", LocalizationRU.DEFAULT_NEXT_STEP_QUESTION)
        assertEquals("В чем бы вы хотели разобраться подробнее?", LocalizationRU.WHAT_DO_YOU_WANT_TO_DISCUSS_FURTHER)
    }

    /** Варианты и итоги шагов рассуждения. */
    @Test
    fun reasoningSteps() {
        // Act & Assert.
        assertEquals("Можно заключить, что ваза хрупкая.", LocalizationRU.WE_CAN_CONCLUDE_THAT("ваза хрупкая"))
        assertEquals("Итак, мы обсудили, почему ваза хрупкая.", LocalizationRU.SO_WEVE_DISCUSSED_WHY("ваза хрупкая"))
        assertEquals("Мы уже говорили о том, что ваза хрупкая.", LocalizationRU.WE_ALREADY_DISCUSSED_THAT("ваза хрупкая"))
        assertEquals("Необходимо проверить, что ваза хрупкая", LocalizationRU.WE_NEED_TO_CHECK_THAT("ваза хрупкая"))
    }

    /** Регрессия: «тоже удовлетворяет условию» переведено и начинается с заглавной буквы. */
    @Test
    fun alsoFitsTheCriteriaIsRussianSentence() {
        // Act.
        val text = LocalizationRU.ALSO_FITS_THE_CRITERIA("оператор +")

        // Assert.
        assertEquals("Оператор + тоже удовлетворяет условию.", text)
    }

    /** Объяснения ответа на вопрос-сопоставление. */
    @Test
    fun aggregationExplanations() {
        // Act & Assert.
        assertEquals("Это неверно, поскольку ваза легкая.", LocalizationRU.AGGREGATION_INCORRECT_BRANCHES_DESCR("ваза легкая"))
        assertEquals(
            "Это неверно, поскольку вы не упомянули, что ваза легкая - это влияет на ситуацию в данном случае.",
            LocalizationRU.AGGREGATION_MISSED_BRANCHES_DESCR_PRIMARY("ваза легкая"),
        )
        assertEquals(
            "Вы также не упомянули, что ваза легкая - это влияет на ситуацию в данном случае.",
            LocalizationRU.AGGREGATION_MISSED_BRANCHES_DESCR_CONCAT("ваза легкая"),
        )
    }

    /** Объяснение верного сопоставления - законченное предложение с точкой. */
    @Test
    fun aggregationCorrectExplanationIsSentence() {
        // Act.
        val text = LocalizationRU.AGGREGATION_CORRECT_EXPL("посылку можно отправить", "ваза легкая")

        // Assert.
        assertEquals("Вы верно оценили ситуацию, однако это значит, что посылку можно отправить - из-за того, что ваза легкая.", text)
    }

    /** Объяснение результата агрегации AND/OR зависит от метода и от того, выполняются ли факторы. */
    @Test
    fun simAggregationExplanation() {
        // Act & Assert.
        assertEquals(
            "Это неверно. Чтобы понять, что посылку можно отправить, все из описанных выше факторов (ваза легкая, ваза целая) должны выполняться. В данном случае так и происходит.",
            LocalizationRU.SIM_AGGREGATION_EXPLANATION(AggregationMethod.AND, "посылку можно отправить", "ваза легкая, ваза целая", true),
        )
        assertEquals(
            "Это неверно. Чтобы понять, что посылку можно отправить, хотя бы один из описанных выше факторов (ваза легкая, ваза целая) должен выполняться. Однако в данном случае это не так.",
            LocalizationRU.SIM_AGGREGATION_EXPLANATION(AggregationMethod.OR, "посылку можно отправить", "ваза легкая, ваза целая", false),
        )
        assertEquals(
            "Это неверно, поскольку в данной ситуации все из описанных выше факторов (ваза легкая) не влияют на решение, а значит, об общем результате в данном случае говорить не приходится.",
            LocalizationRU.SIM_AGGREGATION_NULL_EXPLANATION("ваза легкая"),
        )
    }

    /** Вопросы о значениях свойств склоняют название объекта в родительный падеж. */
    @Test
    fun propertyQuestions() {
        // Act & Assert.
        assertEquals("Имеет ли цвет вазы значение красный?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_CONSTANT("цвет", "ваза", "красный"))
        assertEquals("Сравните значение веса вазы со значением 3", LocalizationRU.COMPARE_A_PROPERTY("вес", "ваза", "3"))
        assertEquals("Каково значение веса вазы?", LocalizationRU.CHECK_OBJ_PROPERTY_OR_CLASS("вес", "ваза"))
        assertEquals("Сравните вес вазы с весом мяча", LocalizationRU.COMPARE_A_PROPERTY_WITH_SAME_PROPS_OF_DIFF_OBJ("вес", "ваза", "мяч"))
    }

    /** Строгие сравнения с числом: «Больше ли» и «Меньше ли». */
    @Test
    fun strictNumericComparisons() {
        // Act & Assert.
        assertEquals("Больше ли вес вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("вес", "ваза", "3", ComparisonOperator.Greater))
        assertEquals("Меньше ли вес вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("вес", "ваза", "3", ComparisonOperator.Less))
    }

    /** Регрессия: нестрогое сравнение спрашивается через противоположное строгое (ответы при этом инвертируются). */
    @Test
    fun nonStrictComparisonsAskOppositeStrict() {
        // Act & Assert.
        assertEquals("Меньше ли вес вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("вес", "ваза", "3", ComparisonOperator.GreaterEqual))
        assertEquals("Больше ли вес вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("вес", "ваза", "3", ComparisonOperator.LessEqual))
        assertEquals("Меньше ли вес вазы веса мяча?", LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("вес", "ваза", "мяч", ComparisonOperator.GreaterEqual))
    }

    /** Сравнение на равенство согласует «Равен/Равна/Равно» с родом названия свойства. */
    @Test
    fun equalityAgreesWithPropertyGender() {
        // Act & Assert.
        assertEquals("Равен ли вес вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("вес", "ваза", "3", ComparisonOperator.Equal))
        assertEquals("Равна ли длина вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("длина", "ваза", "3", ComparisonOperator.NotEqual))
        assertEquals("Равно ли значение вазы 3?", LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("значение", "ваза", "3", ComparisonOperator.Equal))
    }

    /** Свойство, род которого не определить, сравнивается с числом через «Равно ли». */
    @Test
    fun equalityWithUnknownGenderUsesNeuter() {
        // Act.
        val text = LocalizationRU.COMPARE_A_PROPERTY_TO_A_NUMERIC_CONST("weight", "ваза", "3", ComparisonOperator.Equal)

        // Assert.
        assertEquals("Равно ли weight вазы 3?", text)
    }

    /** Сравнение свойств двух объектов: на равенство второе свойство в дательном падеже, иначе - в родительном. */
    @Test
    fun comparisonOfTwoObjectsProperties() {
        // Act & Assert.
        assertEquals("Равен ли вес вазы весу мяча?", LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("вес", "ваза", "мяч", ComparisonOperator.Equal))
        assertEquals("Равна ли длина вазы длине мяча?", LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("длина", "ваза", "мяч", ComparisonOperator.NotEqual))
        assertEquals("Больше ли вес вазы веса мяча?", LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("вес", "ваза", "мяч", ComparisonOperator.Greater))
        assertEquals("Меньше ли вес вазы веса мяча?", LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("вес", "ваза", "мяч", ComparisonOperator.Less))
    }

    /** Регрессия: при неопределимом роде свойства вопрос о равенстве свойств двух объектов формулируется целиком. */
    @Test
    fun comparisonOfTwoObjectsWithUnknownGenderIsFullQuestion() {
        // Act.
        val text = LocalizationRU.COMPARE_WITH_SAME_PROPS_OF_DIFF_OBJ("weight", "ваза", "мяч", ComparisonOperator.Equal)

        // Assert.
        assertEquals("Равно ли weight вазы weight мяча?", text)
    }

    /** Вопросы о классе объекта используют творительный падеж класса. */
    @Test
    fun classQuestions() {
        // Act & Assert.
        assertEquals("Каким предметом является ваза?", LocalizationRU.CHECK_OBJECT_CLASS("предмет", "ваза"))
        assertEquals("Является ли ваза предметом?", LocalizationRU.IS_OBJ_A_CLASS("предмет", "ваза"))
        assertEquals("У вазы больше", LocalizationRU.GREATER_THAN("ваза"))
    }

    /** Утверждения для объяснений о свойствах и классах. */
    @Test
    fun assertions() {
        // Act & Assert.
        assertEquals("вес вазы имеет значение 5", LocalizationRU.COMPARE_PROP_EXPL("вес", "ваза", "5"))
        assertEquals("ваза является предметом", LocalizationRU.CHECK_OBJ_CLASS_EXPL("предмет", "ваза"))
        assertEquals("вес вазы имеет значение 5", LocalizationRU.DEFAULT_PROP_ASSERTION("вес", "ваза", "5"))
    }

    /** Регрессия: утверждения о двух объектах соединяются кириллической «а», без латиницы. */
    @Test
    fun twoObjectsExplanationUsesCyrillicConjunction() {
        // Act.
        val text = LocalizationRU.COMPARE_PROP_OF_DIFF_OBJS_EXPL("вес вазы имеет значение 5", "вес мяча имеет значение 1")

        // Assert.
        assertEquals("вес вазы имеет значение 5, а вес мяча имеет значение 1", text)
        assertFalse(latin.containsMatchIn(text), text)
    }
}
