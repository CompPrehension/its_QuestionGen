package its.questions.gen.strategies

import its.model.nodes.QuestionNode
import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.pick
import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import its.questions.gen.states.QuestionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Вопросы [SequentialStrategy] к узлам-вопросам и вопросы о следующем шаге рассуждения.
 */
class SequentialStrategyQuestionNodeTest {

    private val shop = """
        enum Color { red [ RU.localizedName = "красный" ; EN.localizedName = "red" ; ], blue [ RU.localizedName = "синий" ; EN.localizedName = "blue" ; ] }
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; EN.localizedName = "weight" ; ] ;
            obj prop price: int [ RU.localizedName = "цена" ; EN.localizedName = "price" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; EN.localizedName = "fragility" ; ] ;
            obj prop color: Color [ RU.localizedName = "цвет" ; EN.localizedName = "color" ; ] ;
        } [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item { weight = 5 ; price = 10 ; fragile = true ; color = Color:red ; } [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
        obj b : item { weight = 1 ; price = 10 ; fragile = false ; color = Color:blue ; } [ RU.localizedName = "коробка" ; EN.localizedName = "box" ; ]
    """

    /** Вопрос узла берётся из шаблона, варианты - из текстов исходов; неверный ответ объясняется объяснением верного исхода. */
    @Test
    fun templatedQuestionWithOutcomeTexts() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -[fragileYes]-> { conclude: error };
                    false -[fragileNo]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; ]
            meta for fragile [ alias = "fragile"; RU.question = "Хрупкая ли ${X}[case='и']?"; ]
            meta for fragileYes [ RU.text = "Да, хрупкая"; RU.explanation = "Это неверно: ${X}[case='и'] сделана из стекла."; ]
            meta for fragileNo [ RU.text = "Нет, прочная"; RU.explanation = "Это неверно: ${X}[case='и'] прочная."; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val question = question(state.getQuestion(situation))
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("Нет, прочная")))
        val right = state.proceedWithAnswer(situation, listOf(question.indexOf("Да, хрупкая")))

        // Assert.
        assertEquals("Хрупкая ли ваза?", question.text)
        assertEquals(QuestionType.single, question.type)
        assertEquals(listOf("Да, хрупкая", "Нет, прочная"), question.optionTexts())
        assertEquals(Explanation("Это неверно: ваза сделана из стекла.", ExplanationType.Error), wrong.explanation)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
    }

    /** Без шаблонов вопрос, варианты и объяснение генерируются по выражению узла. */
    @Test
    fun generatedQuestionAnswersAndExplanation() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask (X.weight > 3) {
                    true -> { conclude: error };
                    false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                } as heavy;
            }
            [ alias = "parcel"; ]
            meta for heavy [ alias = "heavy"; ]
            meta for fragile [ alias = "fragile"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("heavy")]!!
        val situation = situation(domain(shop), "EN", "X" to "a")

        // Act.
        val question = question(state.getQuestion(situation))
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("No")))

        // Assert.
        assertEquals("Is weight of vase greater than 3?", question.text)
        assertEquals(listOf("Yes", "No"), question.optionTexts())
        assertEquals(Explanation("That's incorrect, because weight of vase is 5.", ExplanationType.Error), wrong.explanation)
    }

    /** Исходы-значения перечисления подписываются локализованными именами значений. */
    @Test
    fun enumOutcomesAreLocalized() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask (X.color) {
                    Color:red -> { conclude: error };
                    Color:blue -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                } as color;
            }
            [ alias = "parcel"; ]
            meta for color [ alias = "color"; ]
            meta for fragile [ alias = "fragile"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("color")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val question = question(state.getQuestion(situation))
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("красный")))

        // Assert.
        assertEquals("Каково значение цвета коробки?", question.text)
        assertEquals(listOf("красный", "синий"), question.optionTexts())
        assertEquals(Explanation("Это неверно, поскольку цвет коробки имеет значение синий.", ExplanationType.Error), wrong.explanation)
    }

    /** Если выражение не поддерживается генератором, варианты - локализованные значения исходов, а ошибка - просто «Это неверно.». */
    @Test
    fun fallbackAnswersForUnsupportedExpression() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (forAny item i { $i.weight > X.weight }) {
                    true -> { conclude: error };
                    false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                } as heaviest;
            }
            [ alias = "parcel"; ]
            meta for heaviest [ alias = "heaviest"; RU.question = "Есть ли предмет тяжелее, чем ${X}[case='и']?"; ]
            meta for fragile [ alias = "fragile"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("heaviest")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val question = question(state.getQuestion(situation))
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("Нет")))

        // Assert.
        assertEquals("Есть ли предмет тяжелее, чем коробка?", question.text)
        assertEquals(listOf("Да", "Нет"), question.optionTexts())
        assertEquals(Explanation("Это неверно.", ExplanationType.Error, shouldPause = false), wrong.explanation)
    }

    /** Узел-переключатель не задаёт вопроса: автомат молча переходит к шагу после фактического исхода. */
    @Test
    fun switchNodeIsSkippedSilently() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask switch (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val change = change(state.getQuestion(situation))

        // Assert.
        assertNull(change.explanation)
        assertEquals("Какой следующий шаг необходим для решения задачи?", question(change.nextState!!.getQuestion(situation)).text)
    }

    /** Тривиальный в ситуации узел пропускается с объяснением тривиальности без паузы. */
    @Test
    fun trivialNodeIsSkippedWithExplanation() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) with trivial [X.weight > 3] {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; ]
            meta for fragile [ alias = "fragile"; RU.triviality = "${X}[case='и'] тяжелая, её хрупкость уже не важна."; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!

        // Act.
        val trivial = change(state.getQuestion(situation(domain(shop), "RU", "X" to "a")))
        val notTrivial = state.getQuestion(situation(domain(shop), "RU", "X" to "b"))

        // Assert.
        assertEquals(Explanation("ваза тяжелая, её хрупкость уже не важна.", shouldPause = false), trivial.explanation)
        assertEquals("Имеет ли хрупкость коробки значение Да?", question(notTrivial).text)
    }

    /** Тривиальный узел без объяснения тривиальности пропускается молча. */
    @Test
    fun trivialNodeWithoutExplanationIsSkippedSilently() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask (X.fragile) with trivial [X.weight > 3] {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!

        // Act.
        val change = change(state.getQuestion(situation(domain(shop), "RU", "X" to "a")))

        // Assert.
        assertNull(change.explanation)
    }

    /** Тривиальная ветвь (единственный вопрос сразу ведёт к заключениям) вопросов не порождает. */
    @Test
    fun trivialBranchHasNoQuestions() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { conclude: correct };
                } as fragile;
            }
            [ alias = "parcel"; ]
            meta for fragile [ alias = "fragile"; ]
        """)

        // Act.
        val output = SequentialStrategy.buildWithInfo(tree.mainBranch)

        // Assert.
        assertFalse(output.automata.hasQuestions())
        assertEquals(emptyMap(), output.info.nodeStates)
    }

    /** Вопрос о следующем шаге: последующие узлы ветви и заключения о верном/неверном результате ветви. */
    @Test
    fun nextStepQuestionOffersLaterNodesAndConclusions() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val fragile = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")
        val answered = fragile.proceedWithAnswer(situation, listOf(question(fragile.getQuestion(situation)).indexOf("Нет")))
        val nextStep = answered.nextState!!

        // Act.
        val question = question(nextStep.getQuestion(situation))
        val wrong = nextStep.proceedWithAnswer(situation, listOf(question.indexOf("Проверить цену.")))
        val right = nextStep.proceedWithAnswer(situation, listOf(question.indexOf("Проверить вес.")))

        // Assert.
        assertEquals("Какой следующий шаг необходим для решения задачи?", question.text)
        assertEquals(
            listOf("Проверить вес.", "Проверить цену.", "Можно заключить, что коробку можно отправить.", "Можно заключить, что коробку нельзя отправить."),
            question.optionTexts(),
        )
        assertEquals(Explanation("Это неверно.", ExplanationType.Error, shouldPause = false), wrong.explanation)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
        assertEquals("Больше ли вес коробки 3?", question(right.nextState!!.getQuestion(situation)).text)
    }

    /** Если ветвь может завершиться с NULL, в вопросе о следующем шаге есть и заключение о NULL-результате. */
    @Test
    fun nextStepOffersNullConclusionWhenBranchCanBeNull() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: null }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : ($branchResult == BranchResult:ERROR ? 'нельзя' : 'может быть можно')} отправить"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
        """)
        val fragile = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val nextStep = fragile.proceedWithAnswer(situation, listOf(question(fragile.getQuestion(situation)).indexOf("Да"))).nextState!!

        // Act.
        val question = question(nextStep.getQuestion(situation))

        // Assert.
        assertEquals(
            listOf(
                "Проверить вес.",
                "Можно заключить, что вазу можно отправить.",
                "Можно заключить, что вазу нельзя отправить.",
                "Можно заключить, что вазу может быть можно отправить.",
            ),
            question.optionTexts(),
        )
    }

    /** Шаблоны исхода задают вопрос о следующем шаге, формулировки заключений и объяснение ошибки. */
    @Test
    fun outcomeTemplatesShapeNextStepQuestion() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -[fragileYes]-> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
            meta for fragileYes [
                RU.nextStepQuestion = "Что следует из того, что ${X}[case='и'] хрупкая?";
                RU.nextStepExplanation = "Хрупкие предметы отправлять нельзя.";
                RU.nextStepBranchResult_correct = "Её можно отправить.";
                RU.nextStepBranchResult_error = "Её нельзя отправить.";
            ]
        """)
        val fragile = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val nextStep = fragile.proceedWithAnswer(situation, listOf(question(fragile.getQuestion(situation)).indexOf("Да"))).nextState!!

        // Act.
        val question = question(nextStep.getQuestion(situation))
        val wrong = nextStep.proceedWithAnswer(situation, listOf(question.indexOf("Её можно отправить.")))
        val right = nextStep.proceedWithAnswer(situation, listOf(question.indexOf("Её нельзя отправить.")))

        // Assert.
        assertEquals("Что следует из того, что ваза хрупкая?", question.text)
        assertEquals(listOf("Проверить вес.", "Её можно отправить.", "Её нельзя отправить."), question.optionTexts())
        assertEquals(Explanation("Хрупкие предметы отправлять нельзя.", ExplanationType.Error), wrong.explanation)
        assertEquals(ExplanationType.Success, right.explanation!!.type)
    }

    /** Верное заключение о результате ветви запоминает этот результат как предполагаемый и завершает последовательность. */
    @Test
    fun conclusionStoresAssumedResultAndEnds() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
        """)
        val output = SequentialStrategy.buildWithInfo(tree.mainBranch)
        output.automata.finalize(EndQuestionState())
        val fragile = output.info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val transcript = Dialogs.walk(fragile, situation, pick("Да"), pick("Можно заключить, что вазу нельзя отправить."))

        // Assert.
        assertEquals(mapOf("parcel" to its.model.nodes.BranchResult.ERROR), situation.assumedResults)
        assertEquals(
            """
            ? Имеет ли хрупкость вазы значение Да?
              - Да
              - Нет
            > Да
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Проверить вес.
              - Можно заключить, что вазу можно отправить.
              - Можно заключить, что вазу нельзя отправить.
            > Можно заключить, что вазу нельзя отправить.
            ! Success (auto): Верно.
            """.trimIndent(),
            transcript.text,
        )
    }

    /** Последовательный разбор ветви от первого узла: вопросы узлов и шаги до заключения, ошибка шага не прерывает разбор. */
    @Test
    fun sequentialDialogTranscript() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { conclude: correct };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; RU.nextStepExplanation = "Начинать нужно с хрупкости."; ]
            meta for fragile [ alias = "fragile"; RU.asNextStep = "Проверить хрупкость."; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
        """)
        val output = SequentialStrategy.buildWithInfo(tree.mainBranch)
        output.automata.finalize(EndQuestionState())
        val fragile = output.info.nodeStates[tree.element<QuestionNode>("fragile")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val transcript = Dialogs.walk(
            fragile, situation,
            pick("Нет"),
            pick("Можно заключить, что коробку нельзя отправить."),
            pick("Нет"),
            pick("Можно заключить, что коробку можно отправить."),
        )

        // Assert.
        assertEquals(
            """
            ? Имеет ли хрупкость коробки значение Да?
              - Да
              - Нет
            > Нет
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Проверить вес.
              - Можно заключить, что коробку можно отправить.
              - Можно заключить, что коробку нельзя отправить.
            > Можно заключить, что коробку нельзя отправить.
            ! Error (auto): Это неверно.
            ? Больше ли вес коробки 3?
              - Да
              - Нет
            > Нет
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Можно заключить, что коробку можно отправить.
              - Можно заключить, что коробку нельзя отправить.
            > Можно заключить, что коробку можно отправить.
            ! Success (auto): Верно.
            """.trimIndent(),
            transcript.text,
        )
    }

    /** Вопрос о начале рассуждения предлагает среди вариантов и первый узел ветви - верный ответ. */
    @Test
    fun reasoningStartOffersFirstNode() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; RU.nextStepExplanation = "Начинать нужно с хрупкости."; ]
            meta for fragile [ alias = "fragile"; RU.asNextStep = "Проверить хрупкость."; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val automata = SequentialStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val question = question(automata.initState.getQuestion(situation))
        val change = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Проверить хрупкость.")))

        // Assert.
        assertEquals("С чего надо начать, чтобы проверить, что коробку можно отправить?", question.text)
        assertEquals(listOf("Проверить хрупкость.", "Проверить вес.", "Проверить цену."), question.optionTexts())
        assertEquals(ExplanationType.Success, change.explanation!!.type)
    }

    /** Вопрос о начале рассуждения: текст по умолчанию, а неверный выбор объясняется nextStepExplanation ветви. */
    @Test
    fun reasoningStartQuestionTextAndMistake() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; EN.description = "${X} ${$branchResult == BranchResult:CORRECT ? 'can' : 'cannot'} be sent"; EN.nextStepExplanation = "Start with fragility."; ]
            meta for fragile [ alias = "fragile"; EN.asNextStep = "Check fragility."; ]
            meta for heavy [ alias = "heavy"; EN.asNextStep = "Check weight."; ]
            meta for expensive [ alias = "expensive"; EN.asNextStep = "Check price."; ]
        """)
        val automata = SequentialStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "EN", "X" to "b")

        // Act.
        val question = question(automata.initState.getQuestion(situation))
        val change = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Check price.")))

        // Assert.
        assertEquals("What is the first step to determine if box can be sent?", question.text)
        assertEquals(Explanation("Start with fragility.", ExplanationType.Error), change.explanation)
        assertEquals("Is fragility of box equal to Yes?", question(change.nextState!!.getQuestion(situation)).text)
    }

    /** Шаблон nextStepQuestion ветви заменяет текст вопроса о начале рассуждения. */
    @Test
    fun reasoningStartQuestionFromBranchTemplate() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.nextStepQuestion = "С какой проверки начать?"; RU.nextStepExplanation = "Начинать нужно с хрупкости."; ]
            meta for fragile [ alias = "fragile"; RU.asNextStep = "Проверить хрупкость."; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val automata = SequentialStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())

        // Act.
        val question = question(automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "b")))

        // Assert.
        assertEquals("С какой проверки начать?", question.text)
    }
}
