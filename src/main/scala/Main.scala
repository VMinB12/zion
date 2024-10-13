package zion

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.util.Random
import scala.concurrent.Future
import org.apache.pekko.actor.typed.SpawnProtocol

object Agent:
  sealed trait AgentCommand
  final case class UserTask(task: String, replyTo: ActorRef[User.UserCommand])
      extends AgentCommand
  final case class ToolResult(result: Int, replyTo: ActorRef[User.UserCommand])
      extends AgentCommand

  def apply(tool: ActorRef[Tool.ExecuteTool]): Behavior[AgentCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case UserTask(task, replyTo) =>
          context.log.info("Delegating task to Tool")
          tool ! Tool.ExecuteTool(task, context.self, replyTo)
          Behaviors.same
        case ToolResult(result, replyTo) =>
          context.log.info(s"Received ToolResult with result: $result")
          replyTo ! User.Done(s"Direct response to task: $result")
          Behaviors.same
      }
    }

object Tool:
  final case class ExecuteTool(
      task: String,
      replyTo: ActorRef[Agent.ToolResult],
      user: ActorRef[User.UserCommand])

  def apply(): Behavior[ExecuteTool] =
    Behaviors.receive { (context, message) =>
      // import context.executionContext
      // // Simulate asynchronous LLM call
      // Future {
      val result = /* Call to LLM API */ Random.nextInt(100)
      context.log.info(s"Tool processing task: ${message.task} with result: $result")
      message.replyTo ! Agent.ToolResult(result, message.user)
      // }
      Behaviors.same
    }

object User:
  sealed trait UserCommand
  final case class Start(content: String) extends UserCommand
  final case class Done(content: String) extends UserCommand

  def apply(): Behavior[UserCommand] =
    Behaviors.setup { context =>
      val tool = context.spawn(Tool(), "Tool")
      val agent = context.spawn(Agent(tool), "Agent")
      Behaviors.receiveMessage {
        case Start(content) =>
          agent ! Agent.UserTask(content, context.self)
          Behaviors.same
        case Done(content) =>
          context.log.info(s"Done! ${content}")
          Behaviors.stopped
      }
    }

object AgentQuickstart extends App {
  val system: ActorSystem[User.Start] = ActorSystem(User(), "AgentQuickstart")
  system ! User.Start("Give me back a random integer.")
}
