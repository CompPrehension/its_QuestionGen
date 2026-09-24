package its.questions.gen.strategies

import its.model.definition.types.Obj
import its.model.nodes.WhileCycleNode
import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.pick
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.formulations.TemplatingUtils.bodyNextStepExplanation
import its.questions.gen.formulations.TemplatingUtils.bodyNextStepQuestion
import its.questions.gen.formulations.TemplatingUtils.question
import its.questions.gen.states.EndQuestionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SequentialStrategy] для цикла while: вопрос об условии, переход в тело или к выходу из цикла.
 */
class SequentialStrategyWhileCycleTest {

    private val line = """
        class item {
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
            rel next(item) : linear ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { fragile = false ; next(b) ; } [ RU.localizedName = "книга" ; ]
        obj b : item { fragile = false ; next(c) ; } [ RU.localizedName = "коробка" ; ]
        obj c : item { fragile = true ; } [ RU.localizedName = "чашка" ; ]
    """

    /** Тексты узла while берутся из его шаблонов: вопрос об условии и вопрос/объяснение шага после условия. */
    @Test
    fun whileNodeTexts() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; ]
            meta for walk [
                alias = "walk";
                RU.question = "Есть ли предмет после ${X}[case='р']?";
                RU.bodyNextStepQuestion = "Что делать, если после ${X}[case='р'] есть предмет?";
                RU.bodyNextStepExplanation = "Нужно проверить предмет после ${X}[case='р'].";
            ]
        """)
        val node = tree.element<WhileCycleNode>("walk")
        val situation = situation(domain(line), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("Есть ли предмет после книги?", node.question(situation))
        assertEquals("Что делать, если после книги есть предмет?", node.bodyNextStepQuestion(situation))
        assertEquals("Нужно проверить предмет после книги.", node.bodyNextStepExplanation(situation))
        assertNull(tree.element<WhileCycleNode>("walk").let { tree(treeWithoutBodyTexts).element<WhileCycleNode>("walk").bodyNextStepQuestion(situation) })
    }

    private val treeWithoutBodyTexts = """
        tpg Line(X: item) {
            while (X=>next()) {
                _ -> { conclude: null with (X = X->next) };
                null -> { conclude: correct };
            } as walk;
        }
        meta for walk [ alias = "walk"; ]
    """

    /** Автомат для ветви с циклом while строится, и узлу цикла соответствует состояние вопроса об условии. */
    @Test
    fun whileNodeGetsConditionState() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; ]
            meta for walk [ alias = "walk"; ]
        """)

        // Act.
        val info = SequentialStrategy.buildWithInfo(tree.mainBranch).info

        // Assert.
        assertTrue(tree.element<WhileCycleNode>("walk") in info.nodeStates)
    }

    /** Вопрос об условии цикла предлагает ответы по значению условия («Да/Нет»). */
    @Test
    fun conditionQuestionOffersConditionValues() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; ]
            meta for walk [ alias = "walk"; RU.question = "Есть ли предмет после ${X}[case='р']?"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<WhileCycleNode>("walk")]!!
        val situation = situation(domain(line), "RU", "X" to "a")

        // Act.
        val question = question(state.getQuestion(situation))

        // Assert.
        assertEquals("Есть ли предмет после книги?", question.text)
        assertEquals(listOf("Да", "Нет"), question.optionTexts())
    }

    /** Построение вопроса об условии не выполняет цикл в ситуации диалога и не меняет её переменные. */
    @Test
    fun conditionQuestionDoesNotChangeSituation() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error [ RU.explanation = "следующий предмет хрупкий"; ] }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; ]
            meta for walk [ alias = "walk"; RU.question = "Есть ли предмет после ${X}[case='р']?"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<WhileCycleNode>("walk")]!!
        val situation = situation(domain(line), "RU", "X" to "a")

        // Act.
        state.getQuestion(situation)

        // Assert.
        assertEquals(mapOf("X" to Obj("a")), situation.decisionTreeVariables)
    }

    /** После вопроса об условии автомат может пойти как в тело цикла, так и к выходу из него. */
    @Test
    fun conditionQuestionLeadsToBodyOrExit() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; ]
            meta for walk [ alias = "walk"; ]
        """)

        // Act.
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<WhileCycleNode>("walk")]!!

        // Assert.
        assertEquals(2, state.reachableStates.size)
    }

    /** Неверный шаг после условия цикла объясняется объяснением шага (bodyNextStepExplanation), а не текстом вопроса. */
    @Test
    fun stepAfterConditionIsExplainedByExplanationTemplate() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; RU.description = "ряд ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} упаковать"; ]
            meta for walk [
                alias = "walk";
                RU.bodyNextStepQuestion = "Что делать, если после ${X}[case='р'] есть предмет?";
                RU.bodyNextStepExplanation = "Нужно проверить предмет после ${X}[case='р'].";
            ]
            meta for body [ alias = "body"; RU.description = "предмет после ${X}[case='р'] не хрупкий"; ]
        """)
        val condition = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<WhileCycleNode>("walk")]!!
        val situation = situation(domain(line), "RU", "X" to "a")
        val step = condition.reachableStates.first { question(it.getQuestion(situation)).text == "Что делать, если после книги есть предмет?" }
        val question = question(step.getQuestion(situation))

        // Act.
        val wrong = step.proceedWithAnswer(situation, listOf(question.indexOf("Можно заключить, что ряд нельзя упаковать.")))

        // Assert.
        assertEquals("Нужно проверить предмет после книги.", wrong.explanation!!.text)
    }

    /** Разбор цикла: условие, шаг в тело, повтор цикла со сдвинутой переменной, выход по результату тела и итог ветви. */
    @Test
    fun whileDialogTranscript() {
        // Arrange.
        val tree = tree($$"""
            tpg Line(X: item) {
                while (X=>next()) {
                    _ -[body]-> { ask (X->next.fragile) { true -> { conclude: error }; false -> { conclude: null with (X = X->next) }; } as nextFragile; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as walk;
            }
            [ alias = "line"; RU.description = "ряд ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} упаковать"; ]
            meta for walk [
                alias = "walk";
                RU.question = "Есть ли предмет после ${X}[case='р']?";
                RU.asNextStep = "Перейти к следующему предмету.";
                RU.bodyNextStepQuestion = "Что делать, если после ${X}[case='р'] есть предмет?";
                RU.bodyNextStepExplanation = "Нужно проверить предмет после ${X}[case='р'].";
            ]
            meta for body [ alias = "body"; RU.description = "предмет после ${X}[case='р'] ${$branchResult == BranchResult:ERROR ? 'хрупкий' : 'не хрупкий'}"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(line), "RU", "X" to "a")

        // Act.
        val transcript = Dialogs.walk(
            automata.initState, situation,
            pick("Нет"),
            pick("Необходимо проверить, что предмет после книги не хрупкий"),
            pick("Перейти к следующему предмету."),
            pick("Да"),
            pick("Можно заключить, что ряд можно упаковать."),
            pick("Можно заключить, что ряд нельзя упаковать."),
        )

        // Assert.
        assertEquals(
            """
            ? Есть ли предмет после книги?
              - Да
              - Нет
            > Нет
            ! Error (auto): Это неверно.
            ? Что делать, если после книги есть предмет?
              - Можно заключить, что ряд можно упаковать.
              - Можно заключить, что ряд нельзя упаковать.
              - Необходимо проверить, что предмет после книги не хрупкий
            > Необходимо проверить, что предмет после книги не хрупкий
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Перейти к следующему предмету.
              - Можно заключить, что ряд можно упаковать.
              - Можно заключить, что ряд нельзя упаковать.
            > Перейти к следующему предмету.
            ! Success (auto): Верно.
            ? Есть ли предмет после коробки?
              - Да
              - Нет
            > Да
            ! Success (auto): Верно.
            ? Что делать, если после коробки есть предмет?
              - Можно заключить, что ряд можно упаковать.
              - Можно заключить, что ряд нельзя упаковать.
              - Необходимо проверить, что предмет после коробки не хрупкий
            > Можно заключить, что ряд можно упаковать.
            ! Error: Нужно проверить предмет после коробки.
            ? Какой следующий шаг необходим для решения задачи?
              - Перейти к следующему предмету.
              - Можно заключить, что ряд можно упаковать.
              - Можно заключить, что ряд нельзя упаковать.
            > Можно заключить, что ряд нельзя упаковать.
            ! Success (auto): Верно.
            ! Continue: Итак, мы обсудили, почему ряд нельзя упаковать.
            """.trimIndent(),
            transcript.text,
        )
        assertEquals(Obj("b"), situation.decisionTreeVariables["X"])
    }
}
