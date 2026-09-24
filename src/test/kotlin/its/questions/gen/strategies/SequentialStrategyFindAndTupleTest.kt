package its.questions.gen.strategies

import its.model.nodes.FindActionNode
import its.model.nodes.TupleQuestionNode
import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SequentialStrategy] для узлов поиска (без вопроса, с напоминанием факта) и кортежных вопросов (по вопросу на часть).
 */
class SequentialStrategyFindAndTupleTest {

    private val shop = """
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { weight = 5 ; fragile = true ; } [ RU.localizedName = "ваза" ; ]
        obj b : item { weight = 1 ; fragile = false ; } [ RU.localizedName = "коробка" ; ]
        obj c : item { weight = 9 ; fragile = false ; } [ RU.localizedName = "гиря" ; ]
    """

    /** Узел поиска не спрашивается: напоминается найденный факт, затем - вопрос о следующем шаге по фактическому исходу. */
    @Test
    fun findNodeRemindsFact() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; RU.description = "посылку ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for findY [ alias = "findY"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for yFragile [ alias = "yFragile"; RU.asNextStep = "Проверить хрупкость найденного."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<FindActionNode>("findY")]!!
        val found = situation(domain(shop), "RU", "X" to "a")
        val notFound = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val whenFound = change(state.getQuestion(found))
        val whenNotFound = change(state.getQuestion(notFound))

        // Assert.
        assertEquals(Explanation("Мы уже говорили о том, что коробка легче, чем ваза."), whenFound.explanation)
        assertEquals(
            listOf("Проверить хрупкость найденного.", "Можно заключить, что посылку можно отправить.", "Можно заключить, что посылку нельзя отправить."),
            question(whenFound.nextState!!.getQuestion(found)).optionTexts(),
        )
        assertEquals(Explanation("Мы уже говорили о том, что нет предмета легче, чем коробка."), whenNotFound.explanation)
        assertEquals(
            listOf("Можно заключить, что посылку можно отправить.", "Можно заключить, что посылку нельзя отправить."),
            question(whenNotFound.nextState!!.getQuestion(notFound)).optionTexts(),
        )
    }

    /** Кортежный вопрос задаётся по частям: варианты - тексты исходов части или их значения, ошибка - объяснение верного исхода. */
    @Test
    fun tupleIsAskedPartByPart() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask tuple (X.fragile with [true as tFragile, false as tSturdy] as partFragile; X.weight > 2 with [true as tHeavy, false as tLight] as partHeavy) {
                    (true; true) -> { conclude: error }
                    (true; false) -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as veryHeavy; }
                    (false; true) -> { conclude: correct }
                    (false; false) -> { conclude: correct }
                } as parts;
            }
            [ alias = "parcel"; RU.description = "посылку ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for parts [ alias = "parts"; ]
            meta for partFragile [ RU.question = "Хрупкая ли ${X}[case='и']?"; ]
            meta for partHeavy [ RU.question = "Тяжелая ли ${X}[case='и']?"; ]
            meta for tFragile [ RU.text = "Хрупкая"; RU.explanation = "Это неверно: ${X}[case='и'] хрупкая."; ]
            meta for tSturdy [ RU.text = "Прочная"; RU.explanation = "Это неверно: ${X}[case='и'] прочная."; ]
            meta for veryHeavy [ alias = "veryHeavy"; RU.asNextStep = "Проверить, очень ли тяжелая."; ]
        """)
        val first = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<TupleQuestionNode>("parts")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val fragileQuestion = question(first.getQuestion(situation))
        val fragileAnswer = first.proceedWithAnswer(situation, listOf(fragileQuestion.indexOf("Прочная")))
        val second = fragileAnswer.nextState!!
        val heavyQuestion = question(second.getQuestion(situation))
        val heavyAnswer = second.proceedWithAnswer(situation, listOf(heavyQuestion.indexOf("Нет")))
        val afterTuple = change(heavyAnswer.nextState!!.getQuestion(situation))

        // Assert.
        assertEquals("Хрупкая ли ваза?", fragileQuestion.text)
        assertEquals(listOf("Хрупкая", "Прочная"), fragileQuestion.optionTexts())
        assertEquals(Explanation("Это неверно: ваза хрупкая.", ExplanationType.Error), fragileAnswer.explanation)
        assertEquals("Тяжелая ли ваза?", heavyQuestion.text)
        assertEquals(listOf("Да", "Нет"), heavyQuestion.optionTexts())
        assertEquals(Explanation("Это неверно.", ExplanationType.Error, shouldPause = false), heavyAnswer.explanation)
        assertEquals(
            listOf("Проверить, очень ли тяжелая.", "Можно заключить, что посылку можно отправить.", "Можно заключить, что посылку нельзя отправить."),
            question(afterTuple.nextState!!.getQuestion(situation)).optionTexts(),
        )
    }

    /** Регрессия: узел поиска без объяснения исхода пропускается молча, без напоминания. */
    @Test
    fun findWithoutExplanationIsSkippedSilently() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; RU.description = "посылку ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for findY [ alias = "findY"; ]
            meta for yFragile [ alias = "yFragile"; RU.asNextStep = "Проверить хрупкость найденного."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<FindActionNode>("findY")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val change = change(state.getQuestion(situation))

        // Assert.
        assertEquals(null, change.explanation)
        assertEquals("Какой следующий шаг необходим для решения задачи?", question(change.nextState!!.getQuestion(situation)).text)
    }
}
