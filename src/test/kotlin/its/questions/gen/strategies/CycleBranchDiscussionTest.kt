package its.questions.gen.strategies

import its.model.nodes.BranchResult
import its.questions.gen.Dialogs.match
import its.questions.gen.Dialogs.pickAll
import its.questions.gen.Dialogs.resolve
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.dialog.DialogDriver
import its.questions.gen.states.EndQuestionState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Разбор тела циклической агрегации для одного объекта перебора: вопросы учитывают, что студент предположил об этом объекте.
 */
class CycleBranchDiscussionTest {

    private val packing = """
        class box {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
        } [ RU.localizedName = "коробка" ; ]
        class item {
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
            obj prop wrapped: bool [ RU.localizedName = "упакованность" ; ] ;
            rel inside(box) ;
        } [ RU.localizedName = "предмет" ; ]
        obj box1 : box { weight = 3 ; } [ RU.localizedName = "коробка" ; ]
        obj cup : item { fragile = true ; wrapped = false ; inside(box1) ; } [ RU.localizedName = "чашка" ; ]
        obj book : item { fragile = false ; wrapped = false ; inside(box1) ; } [ RU.localizedName = "книга" ; ]
    """

    private val shipping = $$"""
        tpg Packing(X: box) {
            cycle and ($i=>inside(X)) with item i {
                _ -[body]-> {
                    ask (i.fragile) {
                        true -> { ask (i.wrapped) { true -> { conclude: correct }; false -> { conclude: error }; } as iWrapped; };
                        false -> { conclude: correct };
                    } as iFragile;
                };
                correct -> { conclude: correct };
                error -> { conclude: error };
                null -> { conclude: correct };
            } as contents;
        }
        [ alias = "packing"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
        meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
        meta for body [ alias = "body"; RU.description = "${i}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'не мешает' : 'мешает'} отправке"; ]
        meta for iFragile [ alias = "iFragile"; RU.question = "Хрупкий ли ${i}?"; RU.asNextStep = "Проверить хрупкость."; RU.endingCause = "Из-за хрупкости"; ]
        meta for iWrapped [ alias = "iWrapped"; RU.question = "Упакован ли ${i}?"; RU.asNextStep = "Проверить упаковку."; RU.endingCause = "Из-за упаковки"; ]
    """

    /** Разбор ошибочно сопоставленного объекта спрашивает, почему студент так считает, а не что применимо. */
    @Test
    fun wronglyMatchedObjectBranchAsksWhyStudentThinksSo() {
        // Arrange.
        val tree = tree(shipping)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(packing), "RU", "X" to "box1")
        situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)
        val selection = DialogDriver.start(automata, situation)
        val matching = DialogDriver.answer(selection.state!!, situation, resolve(selection.question!!, pickAll("чашка", "книга")))

        // Act.
        val step = DialogDriver.answer(matching.state!!, situation, resolve(matching.question!!, match("Верно", "Верно")))

        // Assert.
        assertEquals("Почему вы считаете, что чашка не мешает отправке?", step.question!!.text)
        assertEquals(listOf("Из-за упаковки", "Из-за хрупкости"), step.question!!.optionTexts())
    }
}
