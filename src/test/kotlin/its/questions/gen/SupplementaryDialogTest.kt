package its.questions.gen

import its.model.nodes.BranchResult
import its.questions.gen.Dialogs.match
import its.questions.gen.Dialogs.pick
import its.questions.gen.Dialogs.walk
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Question
import its.questions.gen.states.QuestionType
import its.questions.gen.strategies.FullBranchStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Полный вспомогательный диалог так, как его ведёт CompPrehension: автомат FullBranchStrategy до EndQuestionState,
 * главная ветвь заранее считается «верной» (студент ошибся, считая ответ верным), ответы - по сценарию теста.
 */
class SupplementaryDialogTest {

    private val shop = """
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; EN.localizedName = "weight" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; EN.localizedName = "fragility" ; ] ;
        } [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item { weight = 5 ; fragile = true ; } [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
        obj b : item { weight = 1 ; fragile = false ; } [ RU.localizedName = "коробка" ; EN.localizedName = "box" ; ]
        obj c : item { weight = 2 ; fragile = true ; } [ RU.localizedName = "чашка" ; EN.localizedName = "cup" ; ]
        obj d : item { weight = 9 ; fragile = false ; } [ RU.localizedName = "гиря" ; EN.localizedName = "kettlebell" ; ]
    """

    /**
     * Можно ли отправить X вместе с подкладкой: найти подкладку Y (легче X и легче 2), затем AND-проверка «X лёгкий и прочный»,
     * затем хрупкость подкладки. Все тексты - на двух языках.
     */
    private val parcel = $$"""
        tpg Parcel(X: item) {
            var Y: item error(1: item as tooHeavy -> $checked.weight > X.weight) = find item i { $i.weight < X.weight and $i.weight < 2 } {
                true -[found]-> {
                    agg and {
                        _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                        _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                        correct -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                        error -> { conclude: error };
                    } as sending;
                };
                false -[none]-> { conclude: correct };
            } as findY;
        }
        [
            alias = "parcel";
            RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить вместе с подкладкой";
            EN.description = "${X} ${$branchResult == BranchResult:CORRECT ? 'can' : 'cannot'} be sent with a padding";
            RU.nextStepExplanation = "Начинать нужно с поиска подкладки.";
            EN.nextStepExplanation = "Start by finding a padding.";
        ]
        meta for findY [
            alias = "findY";
            RU.question = "Какой предмет подойдёт как подкладка для ${X}[case='р']?"; EN.question = "Which item can pad ${X}?";
            RU.asNextStep = "Найти подкладку."; EN.asNextStep = "Find a padding.";
            RU.endingCause = "Подкладки нет"; EN.endingCause = "There is no padding";
        ]
        meta for found [ RU.explanation = "${Y}[case='и'] подойдёт как подкладка"; EN.explanation = "${Y} can be a padding"; ]
        meta for none [ RU.explanation = "подходящей подкладки нет"; EN.explanation = "there is no suitable padding"; ]
        meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] тяжелее, чем ${X}[case='и']."; EN.explanation = "${$checked} is heavier than ${X}."; ]
        meta for sending [
            alias = "sending";
            RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить";
            EN.description = "${X} ${$branchResult == BranchResult:CORRECT ? 'can' : 'cannot'} be sent";
            RU.asNextStep = "Проверить условия отправки."; EN.asNextStep = "Check the sending conditions.";
            RU.endingCause = "Из-за условий отправки"; EN.endingCause = "Because of the sending conditions";
        ]
        meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; EN.description = "${X} is ${$branchResult == BranchResult:CORRECT ? 'light' : 'heavy'}"; ]
        meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; EN.description = "${X} is ${$branchResult == BranchResult:CORRECT ? 'sturdy' : 'fragile'}"; ]
        meta for yFragile [
            alias = "yFragile";
            RU.question = "Хрупкая ли подкладка ${Y}[case='и']?"; EN.question = "Is the padding ${Y} fragile?";
            RU.asNextStep = "Проверить хрупкость подкладки."; EN.asNextStep = "Check the padding's fragility.";
            RU.endingCause = "Из-за хрупкости подкладки"; EN.endingCause = "Because of the padding's fragility";
        ]
    """

    private val latin = Regex("[A-Za-z]")
    private val cyrillic = Regex("[А-Яа-яЁё]")

    private fun firstOption(question: Question): List<Int> = when (question.type) {
        QuestionType.matching -> question.options.map { 0 }
        QuestionType.multiple -> question.options.map { it.second }
        QuestionType.single -> listOf(question.options.first().second)
    }

    private fun lastOption(question: Question): List<Int> = when (question.type) {
        QuestionType.matching -> question.options.map { question.matchingOptions.size - 1 }
        QuestionType.multiple -> emptyList()
        QuestionType.single -> listOf(question.options.last().second)
    }

    /** Русский диалог: подкладка, причина, напоминание о найденном, шаг, сопоставление, итог узла и итог ветви. */
    @Test
    fun russianDialogTranscript() {
        // Arrange.
        val tree = tree(parcel)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "c")
        situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)

        // Act.
        val transcript = walk(
            automata.initState, situation,
            pick("Подходящей подкладки нет"),
            pick("Подкладки нет"),
            pick("Можно заключить, что чашку нельзя отправить вместе с подкладкой."),
            match("Неверно", "Неверно"),
            pick("Неверно"),
            pick("Можно заключить, что чашку нельзя отправить вместе с подкладкой."),
        )

        // Assert.
        assertEquals(
            """
            ? Какой предмет подойдёт как подкладка для чашки?
              - ваза
              - гиря
              - коробка
              - Подходящей подкладки нет
            > Подходящей подкладки нет
            ! Error: Это неверно. В данной ситуации коробка подойдёт как подкладка.
            ? Почему вы считаете, что чашку можно отправить вместе с подкладкой?
              - Из-за хрупкости подкладки
              - Из-за условий отправки
              - Подкладки нет
            > Подкладки нет
            ! Continue: Давайте разберемся.
            ! Continue: Мы уже говорили о том, что коробка подойдёт как подкладка.
            ? Какой следующий шаг необходим для решения задачи?
              - Проверить условия отправки.
              - Проверить хрупкость подкладки.
              - Можно заключить, что чашку можно отправить вместе с подкладкой.
              - Можно заключить, что чашку нельзя отправить вместе с подкладкой.
            > Можно заключить, что чашку нельзя отправить вместе с подкладкой.
            ! Error (auto): Это неверно.
            ? Пожалуйста, сопоставьте ответы
              - чашка легкая
              - чашка прочная
              = Верно
              = Неверно
            > чашка легкая = Неверно; чашка прочная = Неверно
            ! Error: Это неверно, поскольку чашка легкая.
            ? Верно ли, что чашку можно отправить?
              - Верно
              - Неверно
            > Неверно
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Проверить хрупкость подкладки.
              - Можно заключить, что чашку можно отправить вместе с подкладкой.
              - Можно заключить, что чашку нельзя отправить вместе с подкладкой.
            > Можно заключить, что чашку нельзя отправить вместе с подкладкой.
            ! Success (auto): Верно.
            ! Continue: Итак, мы обсудили, почему чашку нельзя отправить вместе с подкладкой.
            """.trimIndent(),
            transcript.text,
        )
    }

    /** Английский диалог по тому же сценарию. */
    @Test
    fun englishDialogTranscript() {
        // Arrange.
        val tree = tree(parcel)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "EN", "X" to "c")
        situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)

        // Act.
        val transcript = walk(
            automata.initState, situation,
            pick("There is no suitable padding"),
            pick("There is no padding"),
            pick("We can conclude that cup cannot be sent with a padding."),
            match("False", "False"),
            pick("False"),
            pick("We can conclude that cup cannot be sent with a padding."),
        )

        // Assert.
        assertEquals(
            """
            ? Which item can pad cup?
              - vase
              - kettlebell
              - box
              - There is no suitable padding
            > There is no suitable padding
            ! Error: That's incorrect. In this case, box can be a padding.
            ? Why do you think that cup can be sent with a padding?
              - Because of the padding's fragility
              - Because of the sending conditions
              - There is no padding
            > There is no padding
            ! Continue: Let's figure it out.
            ! Continue: We have already seen that box can be a padding.
            ? What is the next reasoning step in this case?
              - Check the sending conditions.
              - Check the padding's fragility.
              - We can conclude that cup can be sent with a padding.
              - We can conclude that cup cannot be sent with a padding.
            > We can conclude that cup cannot be sent with a padding.
            ! Error (auto): That's incorrect.
            ? Please match the options to the answers
              - cup is light
              - cup is sturdy
              = True
              = False
            > cup is light = False; cup is sturdy = False
            ! Error: That's incorrect, because cup is light.
            ? Is it true that cup can be sent?
              - True
              - False
            > False
            ! Success (auto): Correct.
            ? What is the next reasoning step in this case?
              - Check the padding's fragility.
              - We can conclude that cup can be sent with a padding.
              - We can conclude that cup cannot be sent with a padding.
            > We can conclude that cup cannot be sent with a padding.
            ! Success (auto): Correct.
            ! Continue: So, we've discussed why cup cannot be sent with a padding.
            """.trimIndent(),
            transcript.text,
        )
    }

    /** При любых ответах и объектах диалог доходит до конца и завершается итогом о фактическом результате ветви. */
    @Test
    fun everyDialogFinishesWithSummary() {
        // Arrange.
        val tree = tree(parcel)
        val expected = mapOf("a" to "нельзя", "b" to "можно", "c" to "нельзя", "d" to "нельзя")

        // Act.
        val lastLines = expected.keys.flatMap { x ->
            listOf(::firstOption, ::lastOption).map { answerer ->
                val situation = situation(domain(shop), "RU", "X" to x)
                situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)
                x to walk(FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState()).initState, situation, answerer).lines.last()
            }
        }

        // Assert.
        val wrong = lastLines.filter { (x, line) -> !line.startsWith("! Continue: Итак, мы обсудили, почему") || !line.contains(" ${expected[x]} отправить") }
        assertEquals(emptyList(), wrong)
    }

    /** Ни в одном диалоге нет вопросов без вариантов ответа. */
    @Test
    fun noQuestionsWithoutOptions() {
        // Arrange.
        val tree = tree(parcel)

        // Act.
        val empty = listOf("a", "b", "c", "d").flatMap { x ->
            listOf(::firstOption, ::lastOption).flatMap { answerer ->
                val situation = situation(domain(shop), "RU", "X" to x)
                situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)
                walk(FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState()).initState, situation, answerer)
                    .questions.filter { it.options.isEmpty() }.map { "$x: ${it.text}" }
            }
        }

        // Assert.
        assertEquals(emptyList(), empty)
    }

    /** Русский диалог полностью по-русски, английский - полностью по-английски. */
    @Test
    fun dialogsAreInTheirLanguage() {
        // Arrange.
        val tree = tree(parcel)

        // Act.
        val foreign = listOf("RU" to latin, "EN" to cyrillic).flatMap { (language, foreignLetters) ->
            listOf("a", "b", "c", "d").flatMap { x ->
                listOf(::firstOption, ::lastOption).flatMap { answerer ->
                    val situation = situation(domain(shop), language, "X" to x)
                    situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)
                    walk(FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState()).initState, situation, answerer)
                        .lines.map { shownText(it) }.filter { foreignLetters.containsMatchIn(it) }.map { "$language $x: $it" }
                }
            }
        }

        // Assert.
        assertEquals(emptyList(), foreign)
    }

    private fun shownText(transcriptLine: String): String =
        if (transcriptLine.startsWith("! ")) transcriptLine.substringAfter(": ") else transcriptLine.drop(4)
}
