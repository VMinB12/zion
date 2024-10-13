package zion

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.util.Random

object Agent:
  sealed trait AgentCommand
  final case class UserTask(task: String) extends AgentCommand
  final case class ToolResult(result: Int) extends AgentCommand

  def apply(): Behavior[AgentCommand] =
    Behaviors.setup { context =>
      val tool = context.spawn(Tool(), "tool")
      val user = context.spawn(User(), "user")

      Behaviors.receiveMessage {
        case message: UserTask =>
          context.log.info("Delegating task to Tool")
          tool ! Tool.ExecuteTool(message.task)
          Behaviors.same
        case message: ToolResult =>
          context.log.info(s"Received ToolResult with result: ${message.result}")
          user ! User.Done(s"Direct response to task: ${message.result}")
          Behaviors.same
      }
    }

object Tool:
  final case class ExecuteTool(task: String)

  def apply(): Behavior[ExecuteTool] =
    Behaviors.setup { context =>
      val agent = context.spawn(Agent(), "agent")
      Behaviors.receiveMessage { message =>
        val result = Random.nextInt(100) // Generate a random integer result
        context.log.info(s"Tool processing task: ${message.task} with result: $result")
        agent ! Agent.ToolResult(result)
        Behaviors.same
      }
    }

object User:

  sealed trait UserCommand
  final case class Start(content: String) extends UserCommand
  final case class Done(content: String) extends UserCommand

  def apply(): Behavior[UserCommand] =
    Behaviors.setup { context =>
      val agent = context.spawn(Agent(), "agent")

      Behaviors.receiveMessage {
        case message: Start =>
          agent ! Agent.UserTask(message.content)
          Behaviors.same
        case message: Done =>
          context.log.info(s"Done! ${message.content}")
          Behaviors.stopped
      }
    }

object AgentQuickstart extends App:
  val system: ActorSystem[User.UserCommand] = ActorSystem(User(), "AgentQuickstart")

  system ! User.Start("Give me back a random integer.")
