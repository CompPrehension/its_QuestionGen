package its.questions.gen

import its.model.DomainSolvingModel
import its.model.definition.ObjectDef
import its.model.definition.types.Obj
import its.model.nodes.BranchResult
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.formulations.LocalizationEN
import its.questions.gen.formulations.LocalizationRU
import its.model.nodes.ThoughtBranch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class QuestioningSituationTest {

    private val shop = """
        class item {
            obj prop weight: int ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { weight = 5 ; } [ RU.localizedName = "ваза" ; ]
        obj b : item { weight = 1 ; } [ RU.localizedName = "мяч" ; ]
        var X = a
    """

    /** Короткий конструктор берёт переменные дерева из модели и по умолчанию русский язык. */
    @Test
    fun shortConstructorTakesVariablesFromModel() {
        // Act.
        val situation = QuestioningSituation(domain(shop))

        // Assert.
        assertEquals(mapOf("X" to Obj("a")), situation.decisionTreeVariables)
        assertEquals("RU", situation.localizationCode)
        assertSame(LocalizationRU, situation.localization)
        assertEquals(emptyMap(), situation.discussedVariables)
        assertEquals(emptyMap(), situation.givenAnswers)
        assertEquals(emptyMap(), situation.assumedResults)
    }

    /** Полный конструктор сохраняет переданное состояние диалога - так CompPrehension восстанавливает его между шагами. */
    @Test
    fun fullConstructorRestoresDialogState() {
        // Act.
        val situation = QuestioningSituation(
            domain(shop),
            mutableMapOf("X" to Obj("b")),
            mutableMapOf("Y" to "a"),
            mutableMapOf(3 to 1),
            mutableMapOf("main" to BranchResult.ERROR),
            "EN",
        )

        // Assert.
        assertEquals(mapOf("X" to Obj("b")), situation.decisionTreeVariables)
        assertEquals(mapOf("Y" to "a"), situation.discussedVariables)
        assertEquals(1, situation.givenAnswer(3))
        assertEquals(mapOf("main" to BranchResult.ERROR), situation.assumedResults)
        assertSame(LocalizationEN, situation.localization)
    }

    /** Регрессия: модель решения (для вызовов других деревьев) сохраняется в ситуации и передаётся в ситуацию для вычислений. */
    @Test
    fun solvingContextIsKeptForEvaluation() {
        // Arrange.
        val model = domain(shop)
        val solvingModel = DomainSolvingModel(model, emptyMap(), emptyMap())

        // Act.
        val situation = QuestioningSituation(model, mutableMapOf("X" to Obj("a")), mutableMapOf(), mutableMapOf(), mutableMapOf(), "RU", solvingModel)

        // Assert.
        assertSame(solvingModel, situation.solvingContext)
        assertSame(solvingModel, situation.forEval().solvingContext)
        assertNull(situation(model, "RU", "X" to "a").forEval().solvingContext)
    }

    /** Предполагаемые результаты ветвей хранятся по alias ветви. */
    @Test
    fun assumedResultsAreKeyedByBranchAlias() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -[light]-> { conclude: correct };
                    _ -[cheap]-> { conclude: correct };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                };
            }
            [ alias = "main"; ]
            meta for light [ alias = "light"; ]
            meta for cheap [ alias = "cheap"; ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        situation.addAssumedResult(tree.element<ThoughtBranch>("light"), BranchResult.ERROR)

        // Assert.
        assertEquals(mapOf("light" to BranchResult.ERROR), situation.assumedResults)
        assertEquals(BranchResult.ERROR, situation.assumedResult(tree.element<ThoughtBranch>("light")))
        assertNull(situation.assumedResult(tree.element<ThoughtBranch>("cheap")))
    }

    /** Данные ответы хранятся по id состояния. */
    @Test
    fun givenAnswersAreKeyedByStateId() {
        // Arrange.
        val situation = situation(domain(shop), "RU")

        // Act.
        situation.addGivenAnswer(7, 2)
        situation.addGivenAnswer(7, 0)

        // Assert.
        assertEquals(0, situation.givenAnswer(7))
        assertNull(situation.givenAnswer(8))
    }

    /** Ситуация для вычислений - независимая копия: изменения в ней не затрагивают исходную ситуацию. */
    @Test
    fun forEvalIsIndependentCopy() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val copy = situation.forEval()
        copy.domainModel.objects.add(ObjectDef("c", "item"))
        copy.decisionTreeVariables["X"] = Obj("b")

        // Assert.
        assertFalse(copy is QuestioningSituation)
        assertNull(situation.domainModel.objects.get("c"))
        assertEquals(mapOf("X" to Obj("a")), situation.decisionTreeVariables)
    }

    /** Ситуация на неподдерживаемом языке не может выдать локализацию. */
    @Test
    fun unsupportedLanguageHasNoLocalization() {
        // Arrange.
        val situation = situation(domain(shop), "DE")

        // Act & Assert.
        assertIs<NullPointerException>(assertFailsWith<NullPointerException> { situation.localization })
    }
}
