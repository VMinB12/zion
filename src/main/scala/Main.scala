package zion

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.Behavior
import scala.concurrent.Future
import scala.util.Random
import java.util.UUID
import scala.concurrent.ExecutionContext

/* The Agent actor processes tasks, potentially by using tools */
object Agent {
  sealed trait AgentCommand
  final case class UserTask(task: String) extends AgentCommand
  final case class ToolResult(result: Int) extends AgentCommand

  def apply(conversation_id: UUID): Behavior[AgentCommand] =
    Behaviors.setup { context =>
      // The agent is responsible for spawning his own tools and dependencies.
      val tool = context.spawn(Tool(conversation_id), s"Tool-$conversation_id")
      Behaviors.receiveMessage {
        // Dummy behavior: 1. delegate task to tool, 2. receive result from tool, 3. reply back to user
        case UserTask(task) =>
          context.log.info(s"Agent-$conversation_id: Delegating task '$task' to Tool")
          tool ! Tool.ExecuteTool(task)
          Behaviors.same
        case ToolResult(result) =>
          context.log.info(s"Agent-$conversation_id: Received ToolResult with result: $result")
          // todo: Reply back to User. Need a ref somehow.
          Behaviors.same
      }
    }
}

/* Execute the tool as instructed by the Agent */
object Tool {
  sealed trait ToolCommand
  final case class ExecuteTool(task: String) extends ToolCommand
  final private case class WrappedResult(result: Int) extends ToolCommand
  final private case class WrappedFailure(task: String, ex: Throwable) extends ToolCommand

  def apply(conversation_id: UUID): Behavior[ToolCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case ExecuteTool(task) =>
          implicit val ec: ExecutionContext = context.executionContext
          // Simulate asynchronous processing
          val resultFuture: Future[Int] = Future {
            // Call to LLM API
            Random.nextInt(100)
          }
          context.pipeToSelf(resultFuture) {
            case scala.util.Success(result) => WrappedResult(result)
            case scala.util.Failure(ex) => WrappedFailure(task, ex)
          }
          Behaviors.same
        case WrappedResult(result) =>
          context.log.info(s"Tool: Completed task for $conversation_id with result: $result")
          // TODO: Reply back to agent with result. replyTo ! Agent.ToolResult(result)
          Behaviors.same
        case WrappedFailure(task, ex) =>
          context.log.error(
            s"Tool: Failed to process task '$task' for $conversation_id, retrying...",
            ex)
          // Retry the task
          context.self ! ExecuteTool(task)
          Behaviors.same
      }
    }
}

/* We User actor receives end-user messages and sends them off to agents for processing. */
object User {
  sealed trait UserCommand
  final case class Start(content: String, conversation_id: UUID) extends UserCommand
  final case class Done(content: String, conversation_id: UUID) extends UserCommand

  def apply(): Behavior[UserCommand] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Start(content, conversation_id) =>
          context.log.info(s"User: Starting task with content '$content'")
          // TODO: Only spawn a single agent per conversation_id
          val agent = context.spawn(Agent(conversation_id), s"Agent-${conversation_id}")
          agent ! Agent.UserTask(content)
          Behaviors.same
        case Done(content, conversation_id) =>
          context.log.info(s"User: Received Done for $conversation_id with content '$content'")
          Behaviors.stopped
      }
    }
}

object AgentQuickstart extends App {
  val system: ActorSystem[User.Start] = ActorSystem(User(), "AgentQuickstart")
  system ! User.Start("Give me back a random integer.", UUID.randomUUID())
}
