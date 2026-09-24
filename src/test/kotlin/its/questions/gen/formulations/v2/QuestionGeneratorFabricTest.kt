package its.questions.gen.formulations.v2

import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.definition.types.Clazz
import its.model.definition.types.Comparison
import its.model.nodes.QuestionNode
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.formulations.LocalizationEN
import its.questions.gen.formulations.LocalizationRU
import its.questions.gen.formulations.TemplatingUtils.question
import its.questions.gen.formulations.v2.generation.ComparePropertyOfDiffObjContext
import its.questions.gen.formulations.v2.generation.GigaChatAPI
import its.questions.gen.formulations.v2.generation.QuestionGeneratorFabric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Генерация текстов вопроса, вариантов ответа и объяснения по выражению узла дерева
 * (используется, когда у узла нет шаблона вопроса). Проверяется без GigaChat.
 */
class QuestionGeneratorFabricTest {

    private val shop = $$"""
        enum Color { red [ RU.localizedName = "красный" ; EN.localizedName = "red" ; ], blue [ RU.localizedName = "синий" ; EN.localizedName = "blue" ; ] }
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; EN.localizedName = "weight" ; ] ;
            obj prop price: int [ RU.localizedName = "цена" ; EN.localizedName = "price" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; EN.localizedName = "fragility" ; ] ;
            obj prop color: Color [ RU.localizedName = "цвет" ; EN.localizedName = "color" ; ] ;
            obj prop label: string [ RU.localizedName = "надпись" ; EN.localizedName = "label" ; ] ;
            obj prop size: int [
                RU.localizedName = "размер" ;
                RU.question = "Какого размера ${$object}[case='и']?" ;
                RU.compareValueQuestion = "Размер ${$object}[case='р'] равен ${$value}?" ;
                RU.assertion = "размер ${$object}[case='р'] равен ${$value}" ;
            ] ;
            rel fits(item) [
                RU.assertion = "${$subj}[case='и'] ${$existence ? '' : 'не '}помещается внутрь ${$obj1}[case='р']" ;
            ] ;
            rel likes(item) ;
        } [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        class glass : item [ RU.localizedName = "стеклянный предмет" ; EN.localizedName = "glass item" ; ]
        obj a : glass { weight = 5 ; price = 3 ; fragile = true ; color = Color:red ; label = "осторожно" ; size = 2 ; fits(b) ; } [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
        obj b : item { weight = 1 ; price = 3 ; fragile = false ; color = Color:blue ; label = "мяч" ; size = 1 ; } [ RU.localizedName = "мяч" ; EN.localizedName = "ball" ; ]
    """

    /** По умолчанию генерация не обращается к GigaChat. */
    @Test
    fun gigaChatIsInactiveByDefault() {
        // Act & Assert.
        assertFalse(GigaChatAPI.isActive)
    }

    /** Сравнение числового свойства с числом: вопрос, ответы «Да/Нет» и объяснение со значением свойства. */
    @Test
    fun numericComparison() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.weight > 3"))!!

        // Assert.
        assertEquals("Больше ли вес вазы 3?", context.generate(situation, LocalizationRU))
        assertEquals("Да", context.generateAnswer(situation, LocalizationRU, true))
        assertEquals("Нет", context.generateAnswer(situation, LocalizationRU, false))
        assertEquals("Это неверно, поскольку вес вазы имеет значение 5.", context.generateExplanation(situation, LocalizationRU, true))
    }

    /** Сравнение на неравенство спрашивается как вопрос о равенстве, поэтому ответы «Да/Нет» инвертируются. */
    @Test
    fun notEqualIsAskedAsEqualityWithInvertedAnswers() {
        // Arrange.
        val situation = situation(domain(shop), "EN", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationEN).getContext(OperatorLoqiBuilder.buildExp("X.weight != 3"))!!

        // Assert.
        assertEquals("Is weight of vase equal to 3?", context.generate(situation, LocalizationEN))
        assertEquals("No", context.generateAnswer(situation, LocalizationEN, true))
        assertEquals("Yes", context.generateAnswer(situation, LocalizationEN, false))
    }

    /** Нестрогое сравнение спрашивается через противоположное строгое, поэтому ответы «Да/Нет» инвертируются. */
    @Test
    fun nonStrictComparisonIsAskedAsOppositeStrict() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")
        val fabric = QuestionGeneratorFabric(situation, LocalizationRU)

        // Act.
        val greaterEqual = fabric.getContext(OperatorLoqiBuilder.buildExp("X.weight >= 3"))!!
        val lessEqual = fabric.getContext(OperatorLoqiBuilder.buildExp("X.weight <= 3"))!!

        // Assert.
        assertEquals("Меньше ли вес вазы 3?", greaterEqual.generate(situation, LocalizationRU))
        assertEquals("Нет", greaterEqual.generateAnswer(situation, LocalizationRU, true))
        assertEquals("Больше ли вес вазы 3?", lessEqual.generate(situation, LocalizationRU))
        assertEquals("Нет", lessEqual.generateAnswer(situation, LocalizationRU, true))
    }

    /** Трёхстороннее сравнение числового свойства с числом: «Сравните...», ответы - «Больше/Меньше/Равно». */
    @Test
    fun threeWayNumericComparison() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.weight.compare(3)"))!!

        // Assert.
        assertEquals("Сравните значение веса вазы со значением 3", context.generate(situation, LocalizationRU))
        assertEquals("Больше", context.generateAnswer(situation, LocalizationRU, Comparison.Values.Greater))
        assertEquals("Меньше", context.generateAnswer(situation, LocalizationRU, Comparison.Values.Less))
        assertEquals("Равно", context.generateAnswer(situation, LocalizationRU, Comparison.Values.Equal))
    }

    /** Сравнение перечислимого и строкового свойства с константой: «Имеет ли ... значение ...?». */
    @Test
    fun enumAndStringComparison() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")
        val fabric = QuestionGeneratorFabric(situation, LocalizationRU)

        // Act.
        val color = fabric.getContext(OperatorLoqiBuilder.buildExp("X.color == Color:blue"))!!
        val label = fabric.getContext(OperatorLoqiBuilder.buildExp("X.label == \"мяч\""))!!

        // Assert.
        assertEquals("Имеет ли цвет вазы значение синий?", color.generate(situation, LocalizationRU))
        assertEquals("Это неверно, поскольку цвет вазы имеет значение красный.", color.generateExplanation(situation, LocalizationRU, false))
        assertEquals("Имеет ли надпись вазы значение мяч?", label.generate(situation, LocalizationRU))
    }

    /** Логическое свойство само по себе и под отрицанием спрашивается как сравнение с «Да» и «Нет». */
    @Test
    fun booleanPropertyIsComparedWithConstant() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")
        val fabric = QuestionGeneratorFabric(situation, LocalizationRU)

        // Act.
        val fragile = fabric.getContext(OperatorLoqiBuilder.buildExp("X.fragile"))!!
        val notFragile = fabric.getContext(OperatorLoqiBuilder.buildExp("not X.fragile"))!!

        // Assert.
        assertEquals("Имеет ли хрупкость вазы значение Да?", fragile.generate(situation, LocalizationRU))
        assertEquals("Имеет ли хрупкость вазы значение Нет?", notFragile.generate(situation, LocalizationRU))
        assertEquals("Да", fragile.generateAnswer(situation, LocalizationRU, true))
        assertEquals("Это неверно, поскольку хрупкость вазы имеет значение Да.", notFragile.generateExplanation(situation, LocalizationRU, false))
    }

    /** Запрос значения свойства: «Каково значение ...?» и объяснение с фактическим значением. */
    @Test
    fun propertyValueQuestion() {
        // Arrange.
        val situation = situation(domain(shop), "EN", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationEN).getContext(OperatorLoqiBuilder.buildExp("X.color"))!!

        // Assert.
        assertEquals("What is the color of vase?", context.generate(situation, LocalizationEN))
        assertEquals("That's incorrect, because color of vase is red.", context.generateExplanation(situation, LocalizationEN, true))
    }

    /** Шаблоны свойства (question, compareValueQuestion, assertion) имеют приоритет над формулировками по умолчанию. */
    @Test
    fun propertyTemplatesOverrideDefaults() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")
        val fabric = QuestionGeneratorFabric(situation, LocalizationRU)

        // Act.
        val value = fabric.getContext(OperatorLoqiBuilder.buildExp("X.size"))!!
        val comparison = fabric.getContext(OperatorLoqiBuilder.buildExp("X.size == 3"))!!

        // Assert.
        assertEquals("Какого размера ваза?", value.generate(situation, LocalizationRU))
        assertEquals("Это неверно, поскольку размер вазы равен 2.", value.generateExplanation(situation, LocalizationRU, 2))
        assertEquals("Размер вазы равен 3?", comparison.generate(situation, LocalizationRU))
        assertEquals("Это неверно, поскольку размер вазы равен 2.", comparison.generateExplanation(situation, LocalizationRU, false))
    }

    /** Сравнение одного свойства двух объектов: вопрос, ответы и объяснение со значениями обоих. */
    @Test
    fun comparisonOfTwoObjects() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a", "Y" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.weight > Y.weight"))!!

        // Assert.
        assertEquals("Больше ли вес вазы веса мяча?", context.generate(situation, LocalizationRU))
        assertEquals("Да", context.generateAnswer(situation, LocalizationRU, true))
        assertEquals(
            "Это неверно, поскольку вес вазы имеет значение 5, а вес мяча имеет значение 1.",
            context.generateExplanation(situation, LocalizationRU, true),
        )
    }

    /** Трёхстороннее сравнение одного свойства двух объектов: ответы называют объект, у которого больше. */
    @Test
    fun threeWayComparisonOfTwoObjects() {
        // Arrange.
        val situation = situation(domain(shop), "EN", "X" to "a", "Y" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationEN).getContext(OperatorLoqiBuilder.buildExp("X.weight.compare(Y.weight)"))!!

        // Assert.
        assertEquals("Compare weight of vase with weight of ball", context.generate(situation, LocalizationEN))
        assertEquals("It's greater for vase", context.generateAnswer(situation, LocalizationEN, Comparison.Values.Greater))
        assertEquals("It's greater for ball", context.generateAnswer(situation, LocalizationEN, Comparison.Values.Less))
        assertEquals("Equal", context.generateAnswer(situation, LocalizationEN, Comparison.Values.Equal))
        assertEquals(
            "That's incorrect, because the value of weight of vase is 5 and the value of weight of ball is 1.",
            context.generateExplanation(situation, LocalizationEN, Comparison.Values.Greater),
        )
    }

    /** Сравнение разных свойств двух объектов не считается сравнением одного свойства. */
    @Test
    fun differentPropertiesAreNotComparedAsSame() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a", "Y" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.weight > Y.price"))

        // Assert.
        assertFalse(context is ComparePropertyOfDiffObjContext, context?.generate(situation, LocalizationRU))
    }

    /** Проверка отношения: вопрос и объяснение строятся по утверждению отношения с учётом его наличия. */
    @Test
    fun relationshipCheckUsesAssertion() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a", "Y" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X=>fits(Y)"))!!

        // Assert.
        assertEquals("Верно ли, что ваза помещается внутрь мяча?", context.generate(situation, LocalizationRU))
        assertEquals("Да", context.generateAnswer(situation, LocalizationRU, true))
        assertEquals("Это неверно, поскольку ваза помещается внутрь мяча.", context.generateExplanation(situation, LocalizationRU, true))
        assertEquals("Это неверно, поскольку ваза не помещается внутрь мяча.", context.generateExplanation(situation, LocalizationRU, false))
    }

    /** Отношение без шаблонов не даёт ни вопроса, ни объяснения. */
    @Test
    fun relationshipWithoutTemplatesGivesNothing() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a", "Y" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X=>likes(Y)"))!!

        // Assert.
        assertNull(context.generate(situation, LocalizationRU))
        assertNull(context.generateExplanation(situation, LocalizationRU, true))
    }

    /** Проверка класса: «Является ли ... ...?» и объяснение с фактическим классом объекта. */
    @Test
    fun classCheck() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X is class:glass"))!!

        // Assert.
        assertEquals("Является ли мяч стеклянным предметом?", context.generate(situation, LocalizationRU))
        assertEquals("Это неверно, поскольку мяч является предметом.", context.generateExplanation(situation, LocalizationRU, false))
    }

    /** Ответ о классе объекта - локализованное имя класса; объяснение называет правильный класс. */
    @Test
    fun classValueAnswerAndExplanation() {
        // Arrange.
        val situation = situation(domain(shop), "EN", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationEN).getContext(OperatorLoqiBuilder.buildExp("X.class()"))!!

        // Assert.
        assertEquals("glass item", context.generateAnswer(situation, LocalizationEN, Clazz("glass")))
        assertEquals("That's incorrect, because vase is a glass item.", context.generateExplanation(situation, LocalizationEN, Clazz("glass")))
    }

    /** Регрессия: в шаблонах свойств объект доступен как $object - это не ключевое слово LOQI. */
    @Test
    fun propertyTemplatesSeeObjectVariable() {
        // Arrange.
        val situation = situation(domain($$"""
            class item {
                obj prop size: int [ RU.localizedName = "размер" ; RU.question = "Какого размера ${$object}[case='и']?" ; ] ;
            } [ RU.localizedName = "предмет" ; ]
            obj a : item { size = 2 ; } [ RU.localizedName = "ваза" ; ]
        """), "RU", "X" to "a")

        // Act.
        val text = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.size"))!!.generate(situation, LocalizationRU)

        // Assert.
        assertEquals("Какого размера ваза?", text)
    }

    /** Регрессия: вопрос о классе объекта называет объявленный тип переменной, а не фактический класс объекта (иначе он подсказывает ответ). */
    @Test
    fun classQuestionUsesDeclaredType() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val withDeclared = QuestionGeneratorFabric(situation, LocalizationRU, mapOf("X" to "item")).getContext(OperatorLoqiBuilder.buildExp("X.class()"))!!
        val withoutDeclared = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp("X.class()"))!!

        // Assert.
        assertEquals("Каким предметом является ваза?", withDeclared.generate(situation, LocalizationRU))
        assertEquals("Каким стеклянным предметом является ваза?", withoutDeclared.generate(situation, LocalizationRU))
    }

    /** Вопрос узла дерева о классе объекта берёт объявленный в дереве тип переменной. */
    @Test
    fun treeClassQuestionUsesTreeDeclaration() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.class()) {
                    class:glass -> { conclude: error };
                    class:item -> { conclude: correct };
                } as cls;
            }
            meta for cls [ alias = "cls"; ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val text = tree.element<QuestionNode>("cls").question(situation)

        // Assert.
        assertEquals("Каким предметом является ваза?", text)
    }

    /** Выражение, для которого нет генератора, не даёт контекста. */
    @Test
    fun unsupportedExpressionHasNoContext() {
        // Arrange.
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val context = QuestionGeneratorFabric(situation, LocalizationRU).getContext(OperatorLoqiBuilder.buildExp($$"forAny item i { $i.weight > X.weight }"))

        // Assert.
        assertNull(context)
    }
}
