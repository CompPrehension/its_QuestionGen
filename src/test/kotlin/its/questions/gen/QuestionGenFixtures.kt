package its.questions.gen

import its.model.definition.DomainModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.definition.loqi.TreeLoqiBuilder
import its.model.definition.types.Obj
import its.model.nodes.DecisionTree
import its.model.nodes.DecisionTreeElement
import its.questions.gen.states.Question
import its.questions.gen.states.QuestionStateChange
import its.questions.gen.states.QuestionStateResult
import java.io.StringReader
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Построение моделей, деревьев и ситуаций из LOQI-текстов теста.
 */
object QuestionGenFixtures {

    fun domain(loqi: String): DomainModel {
        val model = DomainLoqiBuilder.buildDomain(StringReader(loqi))
        model.validateAndThrow()
        return model
    }

    fun tree(loqi: String): DecisionTree = TreeLoqiBuilder.buildTree(StringReader(loqi))

    /**
     * Ситуация на языке [localizationCode] ровно с переданными переменными дерева (имя переменной to имя объекта).
     */
    fun situation(model: DomainModel, localizationCode: String, vararg variables: Pair<String, String>): QuestioningSituation {
        return QuestioningSituation(
            model,
            variables.associate { (name, objectName) -> name to Obj(objectName) }.toMutableMap(),
            localizationCode = localizationCode,
        )
    }

    /**
     * Элемент дерева типа [E] (ветвь, узел или исход) с метаданным `alias` = [alias].
     */
    inline fun <reified E : DecisionTreeElement> DecisionTree.element(alias: String): E {
        val found = elements(this).filterIsInstance<E>().filter { it.metadata["alias"] == alias }.toList()
        if (found.size != 1) fail("expected exactly one ${E::class.simpleName} with alias '$alias', found: $found")
        return found.single()
    }

    fun elements(root: DecisionTreeElement): Sequence<DecisionTreeElement> = sequence {
        yield(root)
        root.linkedElements.forEach { yieldAll(elements(it)) }
    }

    fun question(result: QuestionStateResult): Question = assertIs<Question>(result, "ожидался вопрос")

    fun change(result: QuestionStateResult): QuestionStateChange = assertIs<QuestionStateChange>(result, "ожидался пропуск состояния")

    fun Question.optionTexts(): List<String> = options.map { it.first }

    fun Question.matchingTexts(): List<String> = matchingOptions.map { it.first }

    /**
     * Индекс варианта ответа с текстом [text].
     */
    fun Question.indexOf(text: String): Int {
        val index = options.indexOfFirst { it.first == text }
        if (index < 0) fail("no option '$text' in question '${this.text}', options: ${optionTexts()}")
        return options[index].second
    }
}
