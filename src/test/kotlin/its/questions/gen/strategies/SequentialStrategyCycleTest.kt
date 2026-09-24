package its.questions.gen.strategies

import its.model.definition.types.Obj
import its.model.nodes.BranchResult
import its.model.nodes.CycleAggregationNode
import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.matchingTexts
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import its.questions.gen.states.QuestionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Вопросы [SequentialStrategy] к циклической агрегации: выбор объектов перебора, затем сопоставление по объектам.
 */
class SequentialStrategyCycleTest {

    private val packing = """
        class box {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
        } [ RU.localizedName = "коробка" ; ]
        class item {
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
            rel inside(box) ;
        } [ RU.localizedName = "предмет" ; ]
        obj box1 : box { weight = 3 ; } [ RU.localizedName = "коробка" ; ]
        obj box2 : box { weight = 1 ; } [ RU.localizedName = "пустая коробка" ; ]
        obj cup : item { fragile = true ; inside(box1) ; } [ RU.localizedName = "чашка" ; ]
        obj book : item { fragile = false ; inside(box1) ; } [ RU.localizedName = "книга" ; ]
        obj vase : item { fragile = true ; } [ RU.localizedName = "ваза" ; ]
        obj pen : item { fragile = false ; } [ RU.localizedName = "ручка" ; ]
    """

    /** Сначала студент выбирает все объекты перебора; рядом показываются объекты из категорий ошибок. */
    @Test
    fun objectSelectionQuestion() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) error(1: item as fragileOutside -> $checked.fragile and not $checked=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; ]
            meta for fragileOutside [ alias = "fragileOutside"; RU.explanation = "${$checked}[case='и'] не лежит в ${X}[case='п']."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!

        // Act.
        val question = question(state.getQuestion(situation(domain(packing), "RU", "X" to "box1")))

        // Assert.
        assertEquals("Какие предметы лежат в коробке?", question.text)
        assertEquals(QuestionType.multiple, question.type)
        assertEquals(listOf("чашка", "книга", "ваза"), question.optionTexts())
    }

    /** Пропущенный объект объясняется «... тоже удовлетворяет условию.», лишний - объяснением его категории ошибки. */
    @Test
    fun objectSelectionMistakesAreExplained() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) error(1: item as fragileOutside -> $checked.fragile and not $checked=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; ]
            meta for fragileOutside [ alias = "fragileOutside"; RU.explanation = "${$checked}[case='и'] не лежит в ${X}[case='п']."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box1")
        val question = question(state.getQuestion(situation))

        // Act.
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка"), question.indexOf("ваза")))
        val right = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка"), question.indexOf("книга")))

        // Assert.
        assertEquals(Explanation("Это неверно.\nКнига тоже удовлетворяет условию.\nваза не лежит в коробке.", ExplanationType.Error), wrong.explanation)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
    }

    /** После выбора объектов - сопоставление: строка на каждый объект перебора с описанием тела цикла для него. */
    @Test
    fun matchingIsPerIteratedObject() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; RU.description = "${i}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'выдержит' : 'не выдержит'} перевозку"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box1")
        val selected = question(state.getQuestion(situation))
        val matching = state.proceedWithAnswer(situation, listOf(selected.indexOf("чашка"), selected.indexOf("книга"))).nextState!!

        // Act.
        val question = question(matching.getQuestion(situation))
        val change = matching.proceedWithAnswer(situation, listOf(0, 0))

        // Assert.
        assertEquals("Пожалуйста, сопоставьте ответы", question.text)
        assertEquals(listOf("чашка выдержит перевозку", "книга выдержит перевозку"), question.optionTexts())
        assertEquals(listOf("Верно", "Неверно"), question.matchingTexts())
        assertEquals("Это неверно, поскольку чашка не выдержит перевозку.", change.explanation!!.text)
        assertEquals(mapOf("bodycup" to BranchResult.CORRECT, "bodybook" to BranchResult.CORRECT), situation.assumedResults)
    }

    /** При выборе объекта для подробного разбора переменная цикла получает этот объект. */
    @Test
    fun goingIntoBranchSetsCycleVariable() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; RU.description = "${i}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'выдержит' : 'не выдержит'} перевозку"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box1")
        val selected = question(state.getQuestion(situation))
        val matching = state.proceedWithAnswer(situation, listOf(selected.indexOf("чашка"), selected.indexOf("книга"))).nextState!!
        val select = matching.proceedWithAnswer(situation, listOf(0, 1)).nextState!!

        // Act.
        val question = question(select.getQuestion(situation))
        select.proceedWithAnswer(situation, listOf(question.indexOf("Почему книга выдержит перевозку?")))

        // Assert.
        assertEquals(listOf("Почему чашка не выдержит перевозку?", "Почему книга выдержит перевозку?"), question.optionTexts())
        assertEquals(Obj("book"), situation.decisionTreeVariables["i"])
    }

    /** Регрессия: если объектов перебора нет вовсе, вопрос о выборе объектов не задаётся - автомат сразу переходит к шагу после исхода узла. */
    @Test
    fun noCandidatesSkipsObjectSelection() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; ]
            meta for heavyBox [ alias = "heavyBox"; RU.asNextStep = "Проверить вес коробки."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box2")

        // Act.
        val change = change(state.getQuestion(situation))

        // Assert.
        assertNull(change.explanation)
        val nextStep = question(change.nextState!!.getQuestion(situation))
        assertEquals("Какой следующий шаг необходим для решения задачи?", nextStep.text)
        assertEquals(
            listOf("Проверить вес коробки.", "Можно заключить, что пустую коробку можно отправить.", "Можно заключить, что пустую коробку нельзя отправить."),
            nextStep.optionTexts(),
        )
    }

    /** Если объектов перебора нет, а есть только ошибочные, после верного (пустого) выбора сопоставления без строк не бывает. */
    @Test
    fun onlyErroneousCandidatesLeadToNodeOutcome() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) error(1: item as fragileOutside -> $checked.fragile and not $checked=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; RU.description = "${i}[case='и'] выдержит перевозку"; ]
            meta for fragileOutside [ alias = "fragileOutside"; RU.explanation = "${$checked}[case='и'] не лежит в ${X}[case='п']."; ]
            meta for heavyBox [ alias = "heavyBox"; RU.asNextStep = "Проверить вес коробки."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box2")
        val question = question(state.getQuestion(situation))

        // Act.
        val change = state.proceedWithAnswer(situation, emptyList())

        // Assert.
        assertEquals(listOf("чашка", "ваза"), question.optionTexts())
        assertEquals("Какой следующий шаг необходим для решения задачи?", question(change.nextState!!.getQuestion(situation)).text)
    }

    /** Построение текстов сопоставления не оставляет в ситуации переменную цикла и не затирает уже заданную. */
    @Test
    fun matchingTextsDoNotChangeVariables() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for body [ alias = "body"; RU.description = "${i}[case='и'] выдержит перевозку"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val withoutI = situation(domain(packing), "RU", "X" to "box1")
        val withI = situation(domain(packing), "RU", "X" to "box1", "i" to "pen")
        val matchingWithoutI = state.proceedWithAnswer(withoutI, listOf(0, 1)).nextState!!
        val matchingWithI = state.proceedWithAnswer(withI, listOf(0, 1)).nextState!!

        // Act.
        matchingWithoutI.getQuestion(withoutI)
        matchingWithI.getQuestion(withI)

        // Assert.
        assertEquals(mapOf("X" to Obj("box1")), withoutI.decisionTreeVariables)
        assertEquals(mapOf("X" to Obj("box1"), "i" to Obj("pen")), withI.decisionTreeVariables)
    }

    /** Выбор объектов не зависит от уже заданной в ситуации переменной цикла. */
    @Test
    fun objectSelectionIgnoresExistingVariable() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!

        // Act.
        val question = question(state.getQuestion(situation(domain(packing), "RU", "X" to "box1", "i" to "pen")))

        // Assert.
        assertTrue("ручка" !in question.optionTexts())
        assertEquals(listOf("чашка", "книга"), question.optionTexts())
    }

    /** Регрессия: объект перебора, подходящий и под категорию ошибки, показывается один раз - как верный. */
    @Test
    fun correctObjectIsNotRepeatedAsError() {
        // Arrange.
        val tree = tree($$"""
            tpg Packing(X: box) {
                cycle and ($i=>inside(X)) error(1: item as fragileItem -> $checked.fragile) with item i {
                    _ -[body]-> { ask (i.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as iFragile; };
                    correct -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as heavyBox; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as contents;
            }
            [ alias = "packing"; ]
            meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
            meta for fragileItem [ alias = "fragileItem"; RU.explanation = "${$checked}[case='и'] хрупкая."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<CycleAggregationNode>("contents")]!!
        val situation = situation(domain(packing), "RU", "X" to "box1")
        val question = question(state.getQuestion(situation))

        // Act.
        val change = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка"), question.indexOf("книга")))

        // Assert.
        assertEquals(listOf("чашка", "книга", "ваза"), question.optionTexts())
        assertEquals(ExplanationType.Success, change.explanation!!.type)
    }
}
