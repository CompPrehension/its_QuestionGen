package its.questions.gen.states

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionTest {

    /** Варианты и столбцы сопоставления нумеруются по порядку с нуля. */
    @Test
    fun optionsAreIndexed() {
        // Act.
        val question = Question("Сопоставьте", listOf("Ваза легкая", "Ваза целая"), QuestionType.matching, matchingOptions = listOf("Верно", "Неверно"))

        // Assert.
        assertEquals(listOf("Ваза легкая" to 0, "Ваза целая" to 1), question.options)
        assertEquals(listOf("Верно" to 0, "Неверно" to 1), question.matchingOptions)
    }

    /** Вопросом-сопоставлением считается только вопрос типа matching. */
    @Test
    fun onlyMatchingIsAggregation() {
        // Act & Assert.
        assertTrue(Question("?", listOf("a"), QuestionType.matching).isAggregation)
        assertFalse(Question("?", listOf("a"), QuestionType.single).isAggregation)
        assertFalse(Question("?", listOf("a"), QuestionType.multiple).isAggregation)
    }

    /** Тип вопроса: одиночный выбор, множественный выбор, сопоставление. */
    @Test
    fun questionTypes() {
        // Act & Assert.
        assertEquals(listOf(QuestionType.single, QuestionType.multiple, QuestionType.matching), QuestionType.entries.toList())
    }
}
