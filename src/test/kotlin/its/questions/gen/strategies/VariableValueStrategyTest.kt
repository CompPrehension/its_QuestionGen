package its.questions.gen.strategies

import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.pick
import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import its.questions.gen.states.RedirectQuestionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Вопросы [VariableValueStrategy] о значениях переменных, находимых узлами поиска и влияющих на ход рассуждения.
 */
class VariableValueStrategyTest {

    private val shop = """
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { weight = 5 ; fragile = true ; } [ RU.localizedName = "ваза" ; ]
        obj b : item { weight = 1 ; fragile = false ; } [ RU.localizedName = "коробка" ; ]
        obj c : item { weight = 9 ; fragile = false ; } [ RU.localizedName = "гиря" ; ]
    """

    /** Вопрос о значении переменной: объекты из категорий ошибок, найденный объект и вариант «не найдено». */
    @Test
    fun variableQuestionOptions() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > X.weight) = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] тяжелее, чем ${X}[case='и']."; ]
        """)
        val automata = VariableValueStrategy.build(tree.mainBranch)
        automata.finalize(EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val question = question(automata.initState.getQuestion(situation))

        // Assert.
        assertEquals("Какой предмет легче, чем ваза?", question.text)
        assertEquals(listOf("гиря", "коробка", "Нет предмета легче, чем ваза"), question.optionTexts())
    }

    /** Ошибочный объект объясняется категорией ошибки и фактом; «не найдено» - вердиктом и фактом; выбор запоминается. */
    @Test
    fun variableAnswersAreExplainedAndRemembered() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > X.weight) = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] тяжелее, чем ${X}[case='и']."; ]
        """)
        val automata = VariableValueStrategy.build(tree.mainBranch)
        automata.finalize(EndQuestionState())
        val wrongSituation = situation(domain(shop), "RU", "X" to "a")
        val noneSituation = situation(domain(shop), "RU", "X" to "a")
        val rightSituation = situation(domain(shop), "RU", "X" to "a")
        val question = question(automata.initState.getQuestion(wrongSituation))

        // Act.
        val wrong = automata.initState.proceedWithAnswer(wrongSituation, listOf(question.indexOf("гиря")))
        val none = automata.initState.proceedWithAnswer(noneSituation, listOf(question.indexOf("Нет предмета легче, чем ваза")))
        val right = automata.initState.proceedWithAnswer(rightSituation, listOf(question.indexOf("коробка")))

        // Assert.
        assertEquals(Explanation("гиря тяжелее, чем ваза. В данной ситуации коробка легче, чем ваза.", ExplanationType.Error), wrong.explanation)
        assertEquals(Explanation("Это неверно. В данной ситуации коробка легче, чем ваза.", ExplanationType.Error), none.explanation)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
        assertEquals(mapOf("Y" to "c"), wrongSituation.discussedVariables)
        assertEquals(mapOf("Y" to ""), noneSituation.discussedVariables)
        assertEquals(mapOf("Y" to "b"), rightSituation.discussedVariables)
    }

    /** Если искомого объекта нет, верен вариант «не найдено»; без исхода «не найдено» этот вариант называется «Невозможно найти.». */
    @Test
    fun nothingFound() {
        // Arrange.
        val withNone = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > X.weight) = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] тяжелее, чем ${X}[case='и']."; ]
        """)
        val withoutNone = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > 7) = find item i { $i.weight > X.weight and $i.weight < 7 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет тяжелее, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] тяжелее, чем ${X}[case='и']"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] слишком тяжелая."; ]
        """)
        val automataWithNone = VariableValueStrategy.build(withNone.mainBranch).also { it.finalize(EndQuestionState()) }
        val automataWithoutNone = VariableValueStrategy.build(withoutNone.mainBranch).also { it.finalize(EndQuestionState()) }
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val question = question(automataWithNone.initState.getQuestion(situation))
        val right = automataWithNone.initState.proceedWithAnswer(situation, listOf(question.indexOf("Нет предмета легче, чем коробка")))
        val withoutNoneQuestion = question(automataWithoutNone.initState.getQuestion(situation(domain(shop), "RU", "X" to "b")))

        // Assert.
        assertEquals(listOf("ваза", "гиря", "Нет предмета легче, чем коробка"), question.optionTexts())
        assertEquals(ExplanationType.Success, right.explanation!!.type)
        assertEquals(listOf("гиря", "ваза", "Невозможно найти."), withoutNoneQuestion.optionTexts())
    }

    /** Уже обсуждённая переменная повторно не спрашивается. */
    @Test
    fun discussedVariableIsSkipped() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
        """)
        val end = EndQuestionState()
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(end) }
        val situation = situation(domain(shop), "RU", "X" to "a")
        situation.discussedVariables["Y"] = "b"

        // Act.
        val change = change(automata.initState.getQuestion(situation))

        // Assert.
        assertNull(change.explanation)
        assertSame(end, assertIs<RedirectQuestionState>(change.nextState).redirectsTo())
    }

    /** Переменная, чей поиск не выполняется в данной ситуации, не спрашивается. */
    @Test
    fun variableOffActualPathIsSkipped() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        var Y: item = find item i { $i.weight < X.weight and $i.weight < 3 } {
                            true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                            false -[none]-> { conclude: correct };
                        } as findY;
                    };
                } as fragile;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
        """)
        val end = EndQuestionState()
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(end) }

        // Act.
        val offPath = change(automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "a")))
        val onPath = automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "c"))

        // Assert.
        assertSame(end, assertIs<RedirectQuestionState>(offPath.nextState).redirectsTo())
        assertEquals("Какой предмет легче, чем гиря?", question(onPath).text)
    }

    /** Переменная, от которой зависит поиск другой, спрашивается первой и только один раз. */
    @Test
    fun prerequisiteVariableIsAskedFirstAndOnce() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight < X.weight } {
                    true -[foundY]-> {
                        var Z: item = find item j { $j.weight > Y.weight and $j != X } {
                            true -[foundZ]-> { ask (Z.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as zFragile; };
                            false -[noneZ]-> { conclude: correct };
                        } as findZ;
                    };
                    false -[noneY]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for foundY [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for noneY [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for findZ [ alias = "findZ"; RU.question = "Какой другой предмет тяжелее, чем ${Y}[case='и']?"; ]
            meta for foundZ [ RU.explanation = "${Z}[case='и'] тяжелее, чем ${Y}[case='и']"; ]
            meta for noneZ [ RU.explanation = "нет другого предмета тяжелее, чем ${Y}[case='и']"; ]
        """)
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(EndQuestionState()) }
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val transcript = Dialogs.walk(automata.initState, situation, pick("коробка"), pick("гиря"))

        // Assert.
        assertEquals(listOf("Какой предмет легче, чем ваза?", "Какой другой предмет тяжелее, чем коробка?"), transcript.questions.map { it.text })
        assertEquals(mapOf("Y" to "b", "Z" to "c"), situation.discussedVariables)
    }

    /** В полном разборе ветви вопрос о значении переменной задаётся до вопроса о причине завершения. */
    @Test
    fun variableQuestionPrecedesEndingChoice() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; RU.endingCause = "Нет более легкого предмета"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for yFragile [ alias = "yFragile"; RU.endingCause = "Из-за хрупкости найденного"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val first = question(automata.initState.getQuestion(situation))
        val second = question(automata.initState.proceedWithAnswer(situation, listOf(first.indexOf("коробка"))).nextState!!.getQuestion(situation))

        // Assert.
        assertEquals("Какой предмет легче, чем ваза?", first.text)
        assertEquals("Что из перечисленного применимо в данной ситуации?", second.text)
        assertEquals(listOf("Из-за хрупкости найденного", "Нет более легкого предмета"), second.optionTexts())
    }

    /** Регрессия: найденный объект, подходящий и под категорию ошибки, показывается один раз - как верный. */
    @Test
    fun correctObjectIsNotRepeatedAsError() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as notHeavier -> $checked.weight < X.weight) = find item i { $i.weight < X.weight and $i.weight < 3 } {
                    true -[found]-> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -[none]-> { conclude: correct };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет легче, чем ${X}[case='и']?"; ]
            meta for found [ RU.explanation = "${Y}[case='и'] легче, чем ${X}[case='и']"; ]
            meta for none [ RU.explanation = "нет предмета легче, чем ${X}[case='и']"; ]
            meta for notHeavier [ alias = "notHeavier"; RU.explanation = "${$checked}[case='и'] не тяжелее, чем ${X}[case='и']."; ]
        """)
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(EndQuestionState()) }

        // Act.
        val question = question(automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "a")))

        // Assert.
        assertEquals(listOf("коробка", "Нет предмета легче, чем ваза"), question.optionTexts())
    }

    /** Регрессия: поиск без объяснений исходов спрашивается без упоминания найденного факта, а не падает. */
    @Test
    fun findWithoutOutcomeExplanationsIsAskedWithoutFact() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > 7) = find item i { $i.weight > X.weight and $i.weight < 7 } {
                    true -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                } as findY;
            }
            [ alias = "parcel"; ]
            meta for findY [ alias = "findY"; RU.question = "Какой предмет тяжелее, чем ${X}[case='и']?"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] слишком тяжелая."; ]
        """)
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(EndQuestionState()) }
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val question = question(automata.initState.getQuestion(situation))
        val wrongObject = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("гиря")))
        val impossible = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Невозможно найти.")))

        // Assert.
        assertEquals(listOf("гиря", "ваза", "Невозможно найти."), question.optionTexts())
        assertEquals(Explanation("гиря слишком тяжелая.", ExplanationType.Error), wrongObject.explanation)
        assertEquals(Explanation("Это неверно.", ExplanationType.Error), impossible.explanation)
    }

    /** Регрессия: поиск в короткой форме (без текста вопроса) не спрашивается - диалог идёт дальше, а не падает. */
    @Test
    fun shortFindWithoutQuestionIsSkipped() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                var Y: item = find item i { $i.weight > X.weight and $i.weight < 7 };
                ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile;
            }
            [ alias = "parcel"; ]
        """)
        val end = EndQuestionState()
        val automata = VariableValueStrategy.build(tree.mainBranch).also { it.finalize(end) }

        // Act.
        val change = change(automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "b")))

        // Assert.
        assertNull(change.explanation)
        assertSame(end, assertIs<RedirectQuestionState>(change.nextState).redirectsTo())
    }
}
