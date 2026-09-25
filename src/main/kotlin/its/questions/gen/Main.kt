package its.questions.gen

import ConsoleView
import its.model.DomainSolvingModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.nodes.BranchResult
import its.questions.gen.dialog.DialogDriver
import its.questions.gen.states.*
import its.questions.gen.strategies.QuestioningStrategy
import java.io.File
import java.util.*
import javax.swing.SwingUtilities


fun run() {
    val dir = "../inputs/input_examples_expressions_prod"

    val model = DomainSolvingModel(dir, DomainSolvingModel.BuildMethod.LOQI).validate()

    val endState = object : SkipQuestionState(){
        override fun skip(situation: QuestioningSituation): QuestionStateChange {
            return QuestionStateChange(Explanation("Конец"), null)
        }

        override val reachableStates: Collection<QuestionState>
            get() = emptyList()

    }
    val automata =
        QuestioningStrategy.defaultFullBranchStrategy.buildAndFinalize(model.decisionTree.mainBranch, endState)

    println(GeneralQuestionState.stateCount)
    println()

    val i = 2
    val situationDomain = DomainLoqiBuilder.buildDomain(File("$dir/questions/s_$i.loqi").bufferedReader())
    situationDomain.validateAndThrow()

    val situation = QuestioningSituation(situationDomain)
    situation.addAssumedResult(model.decisionTree.mainBranch, BranchResult.CORRECT)
    var step = DialogDriver.start(automata, situation)
//    step = DialogDriver.resume(automata[94], situation)
//    situation.addAssumedResult(DomainModel.decisionTree.getByAlias("right") as ThoughtBranch, true)
    while(true){
        step.explanations.forEach { println(it.text) }
        if(step.explanations.isNotEmpty()) println()
        val question = step.question ?: break
        val answers = question.ask()
        println()
        step = DialogDriver.answer(step.state!!, situation, answers)
    }

    //val q = QuestionGenerator(dir + "_$input\\")
    //q.start(DomainModel.decisionTree, true)

}

fun main(args: Array<String>) {
    val useView = (args.size > 0 && args[0].equals("-v"))
    if (useView) {
        SwingUtilities.invokeLater {
            val c = ConsoleView()
            c.isVisible = true

            //Start other thread that will run Console.run()
            val mainProgram = Thread { run() }
            mainProgram.start()
        }
    } else {
        run()
    }
}

class Prompt<Info>(val text: String, val options : List<Pair<String, Info>>){
    private val scanner = Scanner(System.`in`)

    private fun getAnswers(): List<Int>{
        print("Ваш ответ: ")
        while(true){
            try{
                val input = scanner.nextLine()
                return if(input.isBlank()) emptyList() else input.split(',').map{ str ->str.toInt()}
            }catch (e: NumberFormatException){
                print("Неверный формат ввода. Введите ответ заново: ")
            }
        }
    }

    fun ask(): Info {
        if(options.size == 1)
            return options[0].second
        println()
        println(text)
        println("(Элемент управления программой: укажите номер варианта для выбора дальнейших действий.)")
        options.forEachIndexed {i, option -> println(" ${i+1}. ${option.first}") }

        var answers = getAnswers()
        while(answers.size != 1 || answers.any { it-1 !in options.indices}){
            println("Укажите одно число для ответа")
            answers = getAnswers()
        }
        val answer = answers.single()
        return options[answer-1].second
    }
}