package zion

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import java.util.UUID

object Agent {
  sealed trait AgentCommand
  final case class UserTask(task: String, replyTo: ActorRef[User.UserCommand])
      extends AgentCommand
  final case class ToolResult(taskId: String, result: Int) extends AgentCommand

  def apply(
      tool: ActorRef[Tool.ToolCommand],
      taskToUser: Map[String, ActorRef[User.UserCommand]] = Map.empty): Behavior[AgentCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case UserTask(task, user) =>
          val taskId = UUID.randomUUID().toString
          context.log.info(s"Agent: Delegating task '$task' with taskId '$taskId' to Tool")
          tool ! Tool.ExecuteTool(taskId, task, context.self)
          apply(tool, taskToUser + (taskId -> user)) // Update state
        case ToolResult(taskId, result) =>
          context.log.info(
            s"Agent: Received ToolResult for taskId '$taskId' with result: $result")
          taskToUser.get(taskId) match {
            case Some(user) =>
              user ! User.Done(s"Result for task '$taskId': $result")
              apply(tool, taskToUser - taskId) // Remove entry
            case None =>
              context.log.warn(s"Agent: No User found for taskId '$taskId'")
              Behaviors.same
          }
      }
    }
}

object Tool {
  sealed trait ToolCommand
  final case class ExecuteTool(taskId: String, task: String, replyTo: ActorRef[Agent.ToolResult])
      extends ToolCommand
  final private case class WrappedResult(
      taskId: String,
      result: Int,
      replyTo: ActorRef[Agent.ToolResult])
      extends ToolCommand
  final private case class WrappedFailure(
      taskId: String,
      task: String,
      ex: Throwable,
      replyTo: ActorRef[Agent.ToolResult])
      extends ToolCommand

  def apply(): Behavior[ToolCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case ExecuteTool(taskId, task, replyTo) =>
          // Simulate asynchronous processing
          val resultFuture: Future[Int] = Future {
            // Call to LLM API
            Random.nextInt(100)
          }
          context.pipeToSelf(resultFuture) {
            case scala.util.Success(result) => WrappedResult(taskId, result, replyTo)
            case scala.util.Failure(ex) => WrappedFailure(taskId, task, ex, replyTo)
          }
          Behaviors.same
        case WrappedResult(taskId, result, replyTo) =>
          context.log.info(s"Tool: Completed taskId '$taskId' with result: $result")
          replyTo ! Agent.ToolResult(taskId, result)
          Behaviors.same
        case WrappedFailure(taskId, task, ex, replyTo) =>
          context.log.error(s"Tool: Failed to process taskId '$taskId', retrying...", ex)
          // Retry the task
          context.self ! ExecuteTool(taskId, task, replyTo)
          Behaviors.same
      }
    }
}

object User {
  sealed trait UserCommand
  final case class Start(content: String) extends UserCommand
  final case class Done(content: String) extends UserCommand

  def apply(): Behavior[UserCommand] =
    Behaviors.setup { context =>
      val tool = context.spawn(Tool(), "Tool")
      val agent = context.spawn(Agent(tool), "Agent")

      Behaviors.receiveMessage {
        case Start(content) =>
          context.log.info(s"User: Starting task with content '$content'")
          agent ! Agent.UserTask(content, context.self)
          Behaviors.same
        case Done(content) =>
          context.log.info(s"User: Received Done with content '$content'")
          Behaviors.stopped
      }
    }
}

object AgentQuickstart extends App {
  val system: ActorSystem[User.Start] = ActorSystem(User(), "AgentQuickstart")
  system ! User.Start("Give me back a random integer.")
}
