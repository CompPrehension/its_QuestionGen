package its.questions.gen.visitors

import its.model.nodes.BranchAggregationNode
import its.model.nodes.BranchResultNode
import its.model.nodes.DecisionTreeNode
import its.model.nodes.FindActionNode
import its.model.nodes.QuestionNode
import its.model.definition.loqi.OperatorLoqiBuilder
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.formulations.TemplatingUtils.alias
import its.questions.gen.visitors.GetNodesLCA._static.getNodesLCA
import its.questions.gen.visitors.GetPossibleJumps.Companion.getPossibleJumps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Обход структуры дерева: [GetNodesLCA], [GetPossibleJumps], [GetPossibleEndingNodes], [getUsedVariables].
 */
class TreeStructureVisitorsTest {

    private val shop = """
        class item {
            obj prop weight: int ;
            obj prop fragile: bool ;
        }
        obj a : item { weight = 5 ; fragile = true ; }
        obj b : item { weight = 1 ; fragile = false ; }
    """

    private fun DecisionTreeNode.label(): String = when (this) {
        is BranchResultNode -> "conclude ${value.name.lowercase()}"
        else -> metadata["alias"].toString()
    }

    /** Ближайший общий предок двух узлов - самый глубокий узел, из которого достижимы оба. */
    @Test
    fun lowestCommonAncestor() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error [ alias = "fragileEnd"; ] };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error [ alias = "heavyEnd"; ] };
                            false -> { conclude: correct [ alias = "lightEnd"; ] };
                        } as heavy;
                    };
                } as fragile;
            }
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val fragile = tree.element<QuestionNode>("fragile")
        val heavy = tree.element<QuestionNode>("heavy")
        val fragileEnd = tree.element<BranchResultNode>("fragileEnd")
        val heavyEnd = tree.element<BranchResultNode>("heavyEnd")
        val lightEnd = tree.element<BranchResultNode>("lightEnd")

        // Act & Assert.
        assertSame(heavy, tree.mainBranch.getNodesLCA(heavyEnd, lightEnd))
        assertSame(fragile, tree.mainBranch.getNodesLCA(fragileEnd, lightEnd))
        assertSame(heavy, tree.mainBranch.getNodesLCA(heavy, lightEnd))
        assertSame(heavy, tree.mainBranch.getNodesLCA(heavy, heavy))
        assertSame(fragile, tree.mainBranch.getNodesLCA(fragile, heavy))
        assertSame(heavy, heavy.getNodesLCA(heavyEnd, lightEnd))
    }

    /** Если один из узлов не лежит в поддереве, общего предка нет. */
    @Test
    fun noCommonAncestorOutsideSubtree() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error [ alias = "fragileEnd"; ] };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { conclude: correct [ alias = "lightEnd"; ] };
                        } as heavy;
                    };
                };
            }
            meta for heavy [ alias = "heavy"; ]
        """)

        // Act.
        val lca = tree.element<QuestionNode>("heavy").getNodesLCA(tree.element<BranchResultNode>("fragileEnd"), tree.element<BranchResultNode>("lightEnd"))

        // Assert.
        assertNull(lca)
    }

    /** Возможные переходы из узла - все узлы его поддерева в порядке обхода в глубину, кроме него самого. */
    @Test
    fun possibleJumpsAreSubtreeInDepthFirstOrder() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: null };
                            false -> { conclude: correct };
                        } as heavy;
                    };
                } as fragile;
            }
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val jumps = tree.element<QuestionNode>("fragile").getPossibleJumps(situation).map { it.label() }

        // Assert.
        assertEquals(listOf("conclude error", "heavy", "conclude null", "conclude correct"), jumps)
    }

    /** Из узла поиска переходы идут по фактическому результату поиска. */
    @Test
    fun jumpsFromFindFollowActualResult() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                var Y: item = find item i { $i.weight < X.weight } {
                    true -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as xFragile; };
                } as findY;
            }
            meta for findY [ alias = "findY"; ]
            meta for yFragile [ alias = "yFragile"; ]
            meta for xFragile [ alias = "xFragile"; ]
        """)
        val find = tree.element<FindActionNode>("findY")

        // Act.
        val whenFound = find.getPossibleJumps(situation(domain(shop), "RU", "X" to "a")).map { it.label() }
        val whenNotFound = find.getPossibleJumps(situation(domain(shop), "RU", "X" to "b")).map { it.label() }

        // Assert.
        assertEquals(listOf("yFragile", "conclude error", "conclude correct"), whenFound)
        assertEquals(listOf("xFragile", "conclude error", "conclude correct"), whenNotFound)
    }

    /** Через вложенный узел поиска переходы идут только по ветке «не найдено» - найденная переменная ещё неизвестна. */
    @Test
    fun jumpsThroughNestedFindFollowNotFound() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        var Y: item = find item i { $i.weight < X.weight } {
                            true -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                            false -> { conclude: null };
                        } as findY;
                    };
                } as fragile;
            }
            meta for fragile [ alias = "fragile"; ]
            meta for findY [ alias = "findY"; ]
            meta for yFragile [ alias = "yFragile"; ]
        """)
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val jumps = tree.element<QuestionNode>("fragile").getPossibleJumps(situation).map { it.label() }

        // Assert.
        assertEquals(listOf("conclude error", "findY", "conclude null"), jumps)
    }

    /** Без ситуации конечные узлы ветви - связующие узлы с переходом прямо в заключение. */
    @Test
    fun endingNodesWithoutSituation() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
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
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)

        // Act.
        val ending = GetPossibleEndingNodes(tree.mainBranch).get()

        // Assert.
        assertEquals(setOf("fragile", "heavy"), ending.endingNodes.map { it.alias }.toSet())
        assertNull(ending.correctEndingNode)
    }

    /** Узел агрегации без перехода по результату, который он может дать, сам является конечным. */
    @Test
    fun aggregationWithMissingOutcomeIsEnding() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: null };
                    _ -> { conclude: null };
                    correct -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    error -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as sending;
            }
            meta for sending [ alias = "sending"; ]
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)

        // Act.
        val ending = GetPossibleEndingNodes(tree.mainBranch).get()

        // Assert.
        assertEquals(setOf("sending", "fragile", "heavy"), ending.endingNodes.map { it.alias }.toSet())
    }

    /** Регрессия: агрегация без перехода по результату, который она дать не может, не конечная; узел, ведущий в агрегацию, - тоже. */
    @Test
    fun impossibleMissingOutcomeAndNodeBeforeAggregationAreNotEnding() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.weight > 3) {
                    true -> {
                        agg and {
                            _ -> { conclude: correct };
                            _ -> { conclude: error };
                            correct -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as f1; };
                            error -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as f2; };
                        } as sending;
                    };
                    false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as f3; };
                } as heavy;
            }
            meta for heavy [ alias = "heavy"; ]
            meta for sending [ alias = "sending"; ]
            meta for f1 [ alias = "f1"; ]
            meta for f2 [ alias = "f2"; ]
            meta for f3 [ alias = "f3"; ]
        """)

        // Act.
        val ending = GetPossibleEndingNodes(tree.mainBranch).get()

        // Assert.
        assertEquals(setOf("f1", "f2", "f3"), ending.endingNodes.map { it.alias }.toSet())
    }

    /** Типы переменных дерева собираются из его параметров, узлов поиска и циклов. */
    @Test
    fun declaredVariableTypesAreCollected() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                var Y: item = find item i { $i.weight < X.weight } {
                    true -> {
                        cycle and ($j.weight > Y.weight) with item j {
                            _ -> { conclude: correct };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                            null -> { conclude: correct };
                        };
                    };
                    false -> { conclude: correct };
                };
            }
        """)

        // Act.
        val types = tree.declaredVariableTypes()

        // Assert.
        assertEquals(mapOf("X" to "item", "Y" to "item", "j" to "item"), types)
    }

    /** Узел агрегации без переходов вовсе - конечный. */
    @Test
    fun aggregationWithoutOutcomesIsEnding() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: correct };
                    _ -> { conclude: error };
                } as sending;
            }
            meta for sending [ alias = "sending"; ]
        """)

        // Act.
        val ending = GetPossibleEndingNodes(tree.mainBranch).get()

        // Assert.
        assertEquals(listOf<DecisionTreeNode>(tree.element<BranchAggregationNode>("sending")), ending.endingNodes.toList())
    }

    /** С ситуацией известен фактический конечный узел - последний выполненный связующий узел ветви. */
    @Test
    fun correctEndingNodeIsLastExecutedLinkNode() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
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
            meta for fragile [ alias = "fragile"; ]
            meta for heavy [ alias = "heavy"; ]
        """)

        // Act.
        val forFragile = GetPossibleEndingNodes(tree.mainBranch, situation(domain(shop), "RU", "X" to "a")).get()
        val forSturdy = GetPossibleEndingNodes(tree.mainBranch, situation(domain(shop), "RU", "X" to "b")).get()

        // Assert.
        assertEquals("fragile", forFragile.correctEndingNode!!.alias)
        assertEquals("heavy", forSturdy.correctEndingNode!!.alias)
        assertEquals(setOf("fragile", "heavy"), forFragile.endingNodes.map { it.alias }.toSet())
    }

    /** С ситуацией узлы после поиска учитываются только по фактическому результату поиска. */
    @Test
    fun endingNodesAfterFindFollowActualResult() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                var Y: item = find item i { $i.weight < X.weight } {
                    true -> { ask (Y.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as yFragile; };
                    false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as xFragile; };
                } as findY;
            }
            meta for findY [ alias = "findY"; ]
            meta for yFragile [ alias = "yFragile"; ]
            meta for xFragile [ alias = "xFragile"; ]
        """)

        // Act.
        val staticEnding = GetPossibleEndingNodes(tree.mainBranch).get()
        val situationEnding = GetPossibleEndingNodes(tree.mainBranch, situation(domain(shop), "RU", "X" to "b")).get()

        // Assert.
        assertEquals(setOf("yFragile", "xFragile"), staticEnding.endingNodes.map { it.alias }.toSet())
        assertEquals(setOf("xFragile"), situationEnding.endingNodes.map { it.alias }.toSet())
        assertEquals("xFragile", situationEnding.correctEndingNode!!.alias)
    }

    /** Используемые переменные дерева собираются из всего выражения, литералы не учитываются. */
    @Test
    fun usedVariables() {
        // Arrange.
        val expression = OperatorLoqiBuilder.buildExp($$"X.weight > Y.weight and forAny item i { $i.weight > Z.weight } and obj:a.fragile")

        // Act.
        val variables = expression.getUsedVariables()

        // Assert.
        assertEquals(setOf("X", "Y", "Z"), variables)
    }
}
