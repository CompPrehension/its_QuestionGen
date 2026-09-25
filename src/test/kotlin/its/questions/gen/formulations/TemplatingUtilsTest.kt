package its.questions.gen.formulations

import its.model.definition.types.Obj
import its.model.nodes.BranchAggregationNode
import its.model.nodes.BranchResult
import its.model.nodes.FindActionNode
import its.model.nodes.Outcome
import its.model.nodes.QuestionNode
import its.model.nodes.ThoughtBranch
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.formulations.TemplatingUtils.alias
import its.questions.gen.formulations.TemplatingUtils.asNextStep
import its.questions.gen.formulations.TemplatingUtils.capitalize
import its.questions.gen.formulations.TemplatingUtils.description
import its.questions.gen.formulations.TemplatingUtils.endingCause
import its.questions.gen.formulations.TemplatingUtils.explanation
import its.questions.gen.formulations.TemplatingUtils.getLocalizedName
import its.questions.gen.formulations.TemplatingUtils.interpret
import its.questions.gen.formulations.TemplatingUtils.nextStepBranchResult
import its.questions.gen.formulations.TemplatingUtils.nextStepExplanation
import its.questions.gen.formulations.TemplatingUtils.nextStepQuestion
import its.questions.gen.formulations.TemplatingUtils.nullFormulation
import its.questions.gen.formulations.TemplatingUtils.question
import its.questions.gen.formulations.TemplatingUtils.text
import its.questions.gen.formulations.TemplatingUtils.toCase
import its.questions.gen.formulations.TemplatingUtils.trivialityExplanation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TemplatingUtilsTest {

    private val shop = $$"""
        enum Color { red [ RU.localizedName = "красный" ; EN.localizedName = "red" ; ], blue [ RU.localizedName = "синий" ; EN.localizedName = "blue" ; ] }
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; EN.localizedName = "weight" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; EN.localizedName = "fragility" ; ] ;
            obj prop color: Color [ RU.localizedName = "цвет" ; EN.localizedName = "color" ; ] ;
        } [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item { weight = 5 ; fragile = true ; color = Color:red ; } [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
        obj b : item { weight = 1 ; fragile = false ; color = Color:blue ; } [ RU.localizedName = "мяч" ; EN.localizedName = "ball" ; ]
    """

    /** Переменная дерева в шаблоне заменяется локализованным именем объекта. */
    @Test
    fun variableIsReplacedWithLocalizedName() {
        // Arrange.
        val situationRu = situation(domain(shop), "RU", "X" to "a")
        val situationEn = situation(domain(shop), "EN", "X" to "a")

        // Act & Assert.
        assertEquals("Это ваза", $$"Это ${X}".interpret(situationRu, "RU"))
        assertEquals("It is vase", $$"It is ${X}".interpret(situationEn, "EN"))
    }

    /** Модификатор [case='...'] склоняет подставленное значение. */
    @Test
    fun caseModifierDeclinesValue() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("вес вазы", $$"вес ${X}[case='р']".interpret(situation, "RU"))
        assertEquals("перед вазой", $$"перед ${X}[case='т']".interpret(situation, "RU"))
        assertEquals("к вазе", $$"к ${X}[case='д']".interpret(situation, "RU"))
    }

    /** Значения разных типов подставляются в локализованном виде. */
    @Test
    fun valuesAreLocalized() {
        // Arrange.
        val situationRu = situation(domain(shop), "RU", "X" to "a")
        val situationEn = situation(domain(shop), "EN", "X" to "a")

        // Act & Assert.
        assertEquals("5", $$"${X.weight}".interpret(situationRu, "RU"))
        assertEquals("Да", $$"${X.fragile}".interpret(situationRu, "RU"))
        assertEquals("No", $$"${obj:b.fragile}".interpret(situationEn, "EN"))
        assertEquals("красный", $$"${X.color}".interpret(situationRu, "RU"))
        assertEquals("предмет", $$"${X.class()}".interpret(situationRu, "RU"))
        assertEquals("Больше", $$"${X.weight.compare(3)}".interpret(situationRu, "RU"))
        assertEquals("Less", $$"${obj:b.weight.compare(3)}".interpret(situationEn, "EN"))
        assertEquals("Equal", $$"${X.weight.compare(5)}".interpret(situationEn, "EN"))
    }

    /** Контекстные переменные шаблона доступны через $имя. */
    @Test
    fun contextVariablesAreAvailable() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val text = $$"${$checked} легче, чем ${X}".interpret(situation, "RU", mapOf("checked" to Obj("b")))

        // Assert.
        assertEquals("мяч легче, чем ваза", text)
    }

    /** Любые последовательности пробельных символов схлопываются в один пробел. */
    @Test
    fun whitespaceIsCollapsed() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val text = "Ваза   \n\t хрупкая".interpret(situation, "RU")

        // Assert.
        assertEquals("Ваза хрупкая", text)
    }

    /** Склонение без падежа оставляет именительный падеж; capitalize поднимает только первую букву. */
    @Test
    fun caseAndCapitalizeHelpers() {
        // Act & Assert.
        assertEquals("красная ваза", "красная ваза".toCase(null))
        assertEquals("красной вазы", "красная ваза".toCase(Case.Gen))
        assertEquals("Ваза хрупкая", "ваза хрупкая".capitalize())
        assertEquals("", "".capitalize())
    }

    /** Локализованное имя объекта модели берётся из его метаданных для нужного языка. */
    @Test
    fun localizedNameOfModelObject() {
        // Arrange.
        val model = domain(shop)

        // Act & Assert.
        assertEquals("ваза", Obj("a").getLocalizedName(model, "RU"))
        assertEquals("vase", Obj("a").getLocalizedName(model, "EN"))
    }

    /** Регрессия: у объекта без названия на нужном языке - понятная ошибка, а не текст «null». */
    @Test
    fun missingLocalizedNameFails() {
        // Arrange.
        val model = domain(shop)

        // Act.
        val error = assertFailsWith<IllegalArgumentException> { Obj("a").getLocalizedName(model, "DE") }

        // Assert.
        assertEquals("'${model.objects.get("a")}' doesn't have a DE localized name", error.message)
    }

    /** Описание ветви: для результата берётся шаблон description_<результат>, иначе общий description с $branchResult. */
    @Test
    fun branchDescriptionPrefersResultSpecificTemplate() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                conclude: correct;
            }
            [
                alias = "main";
                RU.description_correct = "${X}[case='и'] можно отправить";
                RU.description = "${X}[case='и'] ${$branchResult == BranchResult:NULL ? 'возможно' : 'нельзя'} отправить";
            ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("ваза можно отправить", tree.mainBranch.description(situation, BranchResult.CORRECT))
        assertEquals("ваза нельзя отправить", tree.mainBranch.description(situation, BranchResult.ERROR))
        assertEquals("ваза возможно отправить", tree.mainBranch.description(situation, BranchResult.NULL))
    }

    /** Ветвь без описания на языке ситуации - ошибка с понятным сообщением. */
    @Test
    fun missingBranchDescriptionFails() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                conclude: correct;
            }
            [ alias = "main"; EN.description = "can be sent"; ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val error = assertFailsWith<IllegalArgumentException> { tree.mainBranch.description(situation, BranchResult.CORRECT) }

        // Assert.
        assertEquals("Branch 'ThoughtBranch (alias=main)' doesn't have a RU description", error.message)
    }

    /** Описание узла агрегации и формулировка «не влияет»: при отсутствии nullFormulation берётся NO_EFFECT языка. */
    @Test
    fun aggregationNodeDescriptionAndNullFormulation() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: correct };
                    _ -> { conclude: correct };
                    correct -> out;
                    error -> { conclude: error };
                } as sending;
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        agg or {
                            _ -> { conclude: correct };
                            _ -> { conclude: correct };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        } as other;
                    };
                } as fragile;
            }
            [ alias = "main"; ]
            meta for sending [ alias = "sending"; RU.description = "посылку можно отправить"; RU.nullFormulation = "Не важно для отправки"; ]
            meta for other [ alias = "other"; ]
        """)
        val sending = tree.element<BranchAggregationNode>("sending")
        val other = tree.element<BranchAggregationNode>("other")
        val situationRu = situation(domain(shop), "RU", "X" to "a")
        val situationEn = situation(domain(shop), "EN", "X" to "a")

        // Act & Assert.
        assertEquals("посылку можно отправить", sending.description(situationRu, BranchResult.ERROR))
        assertEquals("Не важно для отправки", sending.nullFormulation(situationRu))
        assertEquals("Has no effect", sending.nullFormulation(situationEn))
        assertEquals("Не влияет", other.nullFormulation(situationRu))
        assertEquals(
            "Aggregation node 'BranchAggregationNode (alias=other)' doesn't have a RU description",
            assertFailsWith<IllegalArgumentException> { other.description(situationRu, BranchResult.CORRECT) }.message,
        )
    }

    /** Тексты узла вопроса: вопрос, следующий шаг, причина завершения и объяснение тривиальности. */
    @Test
    fun questionNodeTexts() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                ask (X.fragile) with trivial [X.weight > 3] {
                    true -> { conclude: error };
                    false -> { conclude: correct };
                } as fragile;
            }
            [ alias = "main"; ]
            meta for fragile [
                alias = "fragile";
                RU.question = "Хрупкая ли ${X}[case='и']?";
                RU.asNextStep = "Проверить, хрупкая ли ${X}[case='и'].";
                RU.endingCause = "Потому что ${X}[case='и'] хрупкая";
                RU.triviality = "${X}[case='и'] тяжелая - это и так ясно.";
            ]
        """)
        val node = tree.element<QuestionNode>("fragile")
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("Хрупкая ли ваза?", node.question(situation))
        assertEquals("Проверить, хрупкая ли ваза.", node.asNextStep(situation))
        assertEquals("Потому что ваза хрупкая", node.endingCause(situation))
        assertEquals("ваза тяжелая - это и так ясно.", node.trivialityExplanation(situation))
    }

    /** Без обязательных текстов узла - ошибки с понятными сообщениями; без необязательных - null. */
    @Test
    fun missingNodeTexts() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { conclude: correct };
                } as fragile;
            }
            [ alias = "main"; ]
            meta for fragile [ alias = "fragile"; ]
        """)
        val node = tree.element<QuestionNode>("fragile")
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals(
            "Node 'QuestionNode (alias=fragile)' doesn't have a RU associated 'as next step' description",
            assertFailsWith<IllegalArgumentException> { node.asNextStep(situation) }.message,
        )
        assertEquals(
            "Node 'QuestionNode (alias=fragile)' doesn't have a RU ending cause",
            assertFailsWith<IllegalArgumentException> { node.endingCause(situation) }.message,
        )
        assertNull(node.trivialityExplanation(situation))
    }

    /** Вопрос узла без шаблона генерируется по его выражению. */
    @Test
    fun questionIsGeneratedFromExpressionWithoutTemplate() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.weight > 3) {
                    true -> { conclude: error };
                    false -> { conclude: correct };
                } as heavy;
            }
            [ alias = "main"; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val node = tree.element<QuestionNode>("heavy")
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val text = node.question(situation)

        // Assert.
        assertEquals("Больше ли вес вазы 3?", text)
    }

    /** Вопрос по выражению, для которого нет генератора и шаблона, - ошибка. */
    @Test
    fun questionWithoutTemplateAndGeneratorFails() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                ask (forAny item i { $i.weight > X.weight }) {
                    true -> { conclude: error };
                    false -> { conclude: correct };
                } as heaviest;
            }
            [ alias = "main"; ]
            meta for heaviest [ alias = "heaviest"; ]
        """)
        val node = tree.element<QuestionNode>("heaviest")
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val error = assertFailsWith<IllegalArgumentException> { node.question(situation) }

        // Assert.
        assertEquals("Node 'QuestionNode (alias=heaviest)' doesn't have a RU associated question", error.message)
    }

    /** Тексты исходов берутся из их метаданных, а при отсутствии - null. */
    @Test
    fun outcomeTexts() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -[yes]-> { conclude: error };
                    false -[no]-> { conclude: correct };
                } as fragile;
            }
            [ alias = "main"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for yes [
                alias = "yes";
                RU.text = "Да, ${X}[case='и'] хрупкая";
                RU.explanation = "${X}[case='и'] сделана из стекла";
                RU.nextStepQuestion = "Что следует из хрупкости ${X}[case='р']?";
                RU.nextStepExplanation = "Хрупкое нельзя отправлять.";
                RU.nextStepBranchResult_error = "Отправлять ${X}[case='в'] нельзя.";
                RU.nextStepBranchResult = "Об отправке ${X}[case='р'] ничего не известно.";
            ]
        """)
        val yes = tree.element<Outcome<*>>("yes")
        val no = tree.element<QuestionNode>("fragile").outcomes[false]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("Да, ваза хрупкая", yes.text(situation))
        assertEquals("ваза сделана из стекла", yes.explanation(situation))
        assertEquals("Что следует из хрупкости вазы?", yes.nextStepQuestion(situation))
        assertEquals("Хрупкое нельзя отправлять.", yes.nextStepExplanation(situation))
        assertEquals("Отправлять вазу нельзя.", yes.nextStepBranchResult(situation, BranchResult.ERROR))
        assertEquals("Об отправке вазы ничего не известно.", yes.nextStepBranchResult(situation, BranchResult.CORRECT))
        assertNull(no.text(situation))
        assertNull(no.explanation(situation))
        assertNull(no.nextStepQuestion(situation))
        assertNull(no.nextStepExplanation(situation))
        assertNull(no.nextStepBranchResult(situation, BranchResult.CORRECT))
    }

    /** Тексты ветви о следующем шаге: вопрос необязателен, объяснение обязательно. */
    @Test
    fun branchNextStepTexts() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -[light]-> { conclude: correct };
                    _ -[intact]-> { conclude: correct };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "main"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.nextStepQuestion = "С чего начать проверку веса?"; RU.nextStepExplanation = "Сначала взвесьте."; ]
            meta for intact [ alias = "intact"; ]
        """)
        val light = tree.element<ThoughtBranch>("light")
        val intact = tree.element<ThoughtBranch>("intact")
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act & Assert.
        assertEquals("С чего начать проверку веса?", light.nextStepQuestion(situation))
        assertEquals("Сначала взвесьте.", light.nextStepExplanation(situation))
        assertNull(intact.nextStepQuestion(situation))
        assertEquals(
            "Branch 'ThoughtBranch (alias=intact)' doesn't have a RU next step explanation",
            assertFailsWith<IllegalArgumentException> { intact.nextStepExplanation(situation) }.message,
        )
        assertEquals("light", light.alias)
    }

    /** Объяснение категории ошибки поиска получает проверяемый объект как $checked. */
    @Test
    fun findErrorCategoryExplanationGetsCheckedObject() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > 3) = find item i { $i.weight < 3 } {
                    true -> { conclude: correct };
                    false -> { conclude: error };
                } as findY;
            }
            [ alias = "main"; ]
            meta for findY [ alias = "findY"; ]
            meta for tooHeavy [ alias = "tooHeavy"; RU.explanation = "${$checked}[case='и'] слишком тяжелая, в отличие от ${X}[case='р']"; ]
        """)
        val category = tree.element<FindActionNode>("findY").errorCategories.single()
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val text = category.explanation(situation, "a")

        // Assert.
        assertEquals("Ваза слишком тяжелая, в отличие от мяча", text)
    }

    /** Категория ошибки поиска без объяснения - ошибка с понятным сообщением. */
    @Test
    fun missingFindErrorCategoryExplanationFailsWithMessage() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                var Y: item error(1: item as tooHeavy -> $checked.weight > 3) = find item i { $i.weight < 3 } {
                    true -> { conclude: correct };
                    false -> { conclude: error };
                } as findY;
            }
            [ alias = "main"; ]
            meta for findY [ alias = "findY"; ]
        """)
        val category = tree.element<FindActionNode>("findY").errorCategories.single()
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act & Assert.
        assertFailsWith<IllegalArgumentException> { category.explanation(situation, "a") }
    }
}
