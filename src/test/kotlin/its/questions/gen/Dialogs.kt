package its.questions.gen

import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.matchingTexts
import its.questions.gen.dialog.DialogDriver
import its.questions.gen.states.Explanation
import its.questions.gen.states.Question
import its.questions.gen.states.QuestionState
import its.questions.gen.states.QuestionType
import kotlin.test.fail

/**
 * Прохождение автомата вопросов шагами [DialogDriver]:
 * на каждый вопрос дается ответ, пропуски состояний (без вопроса) драйвер проходит сам.
 * Прохождение заканчивается, когда диалог окончен.
 *
 * Результат - текстовая стенограмма диалога:
 * `? текст вопроса`, варианты `  - вариант`, столбцы сопоставления `  = столбец`, данный ответ `> ответ`
 * и объяснения `! Тип: текст` (с пометкой `(auto)`, если объяснение не требует паузы).
 */
object Dialogs {

    private const val MAX_STEPS = 100

    /** Ответ на один вопрос сценария. */
    sealed interface ScriptedAnswer

    /** Выбор одного варианта по тексту. */
    data class Pick(val option: String) : ScriptedAnswer

    /** Выбор нескольких вариантов по тексту (в том числе ни одного). */
    data class PickAll(val options: List<String>) : ScriptedAnswer

    /** Сопоставление: для каждой строки по порядку - текст выбранного столбца. */
    data class Match(val columns: List<String>) : ScriptedAnswer

    fun pick(option: String) = Pick(option)

    fun pickAll(vararg options: String) = PickAll(options.toList())

    fun match(vararg columns: String) = Match(columns.toList())

    class Transcript(val lines: List<String>, val questions: List<Question>) {
        val text: String get() = lines.joinToString("\n")
    }

    /**
     * Пройти автомат от [start], отвечая по сценарию [answers]: i-й ответ - на i-й заданный вопрос.
     * Падает, если вопросов больше, чем ответов, или ответы остались неиспользованными.
     */
    fun walk(start: QuestionState, situation: QuestioningSituation, vararg answers: ScriptedAnswer): Transcript {
        val script = answers.toMutableList()
        val transcript = walk(start, situation) { question ->
            if (script.isEmpty()) fail("no scripted answer for question '${question.text}'")
            resolve(question, script.removeAt(0))
        }
        if (script.isNotEmpty()) fail("unused scripted answers: $script\n${transcript.text}")
        return transcript
    }

    /**
     * Пройти автомат от [start], отвечая на каждый вопрос функцией [answerer] (индексы вариантов, для сопоставления -
     * индексы столбцов по строкам).
     */
    fun walk(start: QuestionState, situation: QuestioningSituation, answerer: (Question) -> List<Int>): Transcript {
        val lines = mutableListOf<String>()
        val questions = mutableListOf<Question>()
        var step = DialogDriver.resume(start, situation)
        repeat(MAX_STEPS) {
            lines.addAll(step.explanations.map(::describe))
            val question = step.question ?: return Transcript(lines, questions)
            questions.add(question)
            lines.addAll(describe(question))
            val answer = answerer(question)
            lines.add("> " + describeAnswer(question, answer))
            step = DialogDriver.answer(step.state!!, situation, answer)
        }
        fail("dialog did not finish in $MAX_STEPS steps:\n" + lines.joinToString("\n"))
    }

    fun resolve(question: Question, answer: ScriptedAnswer): List<Int> {
        return when (answer) {
            is Pick -> {
                if (question.type != QuestionType.single) fail("expected single choice question, got ${question.type}: '${question.text}'")
                listOf(question.indexOf(answer.option))
            }
            is PickAll -> {
                if (question.type != QuestionType.multiple) fail("expected multiple choice question, got ${question.type}: '${question.text}'")
                answer.options.map { question.indexOf(it) }
            }
            is Match -> {
                if (question.type != QuestionType.matching) fail("expected matching question, got ${question.type}: '${question.text}'")
                if (answer.columns.size != question.options.size) fail("matching needs ${question.options.size} answers, got ${answer.columns}")
                answer.columns.map { column ->
                    val index = question.matchingTexts().indexOf(column)
                    if (index < 0) fail("no matching column '$column', columns: ${question.matchingTexts()}")
                    index
                }
            }
        }
    }

    fun describe(question: Question): List<String> {
        return listOf("? ${question.text}") +
                question.options.map { "  - ${it.first}" } +
                question.matchingOptions.map { "  = ${it.first}" }
    }

    fun describe(explanation: Explanation): String {
        return "! ${explanation.type}${if (explanation.shouldPause) "" else " (auto)"}: ${explanation.text}"
    }

    private fun describeAnswer(question: Question, answer: List<Int>): String {
        return when (question.type) {
            QuestionType.matching -> question.options.mapIndexed { row, option ->
                "${option.first} = ${question.matchingOptions[answer[row]].first}"
            }.joinToString("; ")
            else -> answer.joinToString(" | ") { index -> question.options.first { it.second == index }.first }
        }
    }
}
