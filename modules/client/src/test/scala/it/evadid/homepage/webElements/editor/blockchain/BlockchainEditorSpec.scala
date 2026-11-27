package it.evadid.homepage.webElements.editor.blockchain

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.model.blockchain.{TeachingBlock, TeachingChain}
import org.scalajs.dom
import scala.concurrent.ExecutionContext

class BlockchainEditorSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val initial = TeachingChain(Seq(TeachingBlock("Alice pays Bob 5"), TeachingBlock("Bob pays Carol 2")))
  private def text(key: String) = loadingText(s"blockchainworkbook/$key")
  private def blocks(root: dom.Element) = all(root, ".blockchain-block")
  private def miningStatus(root: dom.Element) = one(root, ".blockchain-mining-status").textContent

  test("each block shows its predecessor and hash; editing data or nonce changes the chain and later blocks") {
    val state = Var(initial)
    val editor = BlockchainEditor(pageInfo, state, initial, 1, Val(false))
    assert(!editor.dismissOnOutsideClick)
    mounted(editor.getDomElement()) { root =>
      val List(first, second) = blocks(root): @unchecked
      assertEquals(one(first, ".blockchain-previous").textContent, TeachingChain.genesisPreviousHash)
      assertEquals(one(first, ".blockchain-hash").textContent, initial.hashes.head)
      assertEquals(one(second, ".blockchain-previous").textContent, initial.hashes.head)
      typeInto(one(first, "textarea"), "Alice pays Bob 50")
      assertEquals(state.now().blocks.head.data, "Alice pays Bob 50")
      assertEquals(one(second, ".blockchain-previous").textContent, state.now().hashes.head, "the next block links to the new hash")
      assertNotEquals(state.now().hashes.head, initial.hashes.head)

      val nonce = one(first, "input")
      typeInto(nonce, "12a")
      assertEquals(nonce.getAttribute("aria-invalid"), "true")
      assert(one(first, ".blockchain-error").textContent.contains(text("blockNonceInvalid")))
      assert(isDisabled(buttonNamed(first, text("blockMine"))), "an invalid nonce cannot be mined from")
      assertEquals(state.now().blocks.head.nonce, 0, "an invalid draft is not saved")
      typeInto(nonce, "7")
      assertEquals(state.now().blocks.head.nonce, 7)
      assertEquals(nonce.getAttribute("aria-invalid"), "false")
      assertEquals(one(first, ".blockchain-error").textContent, "")

      typeInto(nonce, "x")
      click(buttonNamed(root, text("blockReset")))
      assertEquals(state.now(), initial)
      assertEquals(valueOf(nonce), "0", "resetting discards invalid drafts")
    }
  }

  test("mining finds a nonce for a block, which then counts as valid") {
    val state = Var(initial)
    mountedAsync(BlockchainEditor(pageInfo, state, initial, 1, Val(false)).getDomElement()) { root =>
      assert(miningStatus(root).contains(text("miningIdle")))
      click(buttonNamed(blocks(root).head, text("blockMine")))
      assert(miningStatus(root).contains(text("miningRunning")))
      eventually(miningStatus(root).contains(text("miningFound")), clue = "mined block").map { _ =>
        val hash = state.now().hashes.head
        assert(hash.startsWith("0"), hash)
        assertEquals(one(blocks(root).head, ".blockchain-hash").textContent, hash)
        assert(blocks(root).head.classList.contains("blockchain-block--valid"))
        assertEquals(valueOf(one(blocks(root).head, "input")), state.now().blocks.head.nonce.toString)
        assert(one(blocks(root).head, ".blockchain-block-status").textContent.contains(text("blockValid")))
        val secondStatus = one(blocks(root)(1), ".blockchain-block-status").textContent
        assert(secondStatus.contains(text("blockInvalid")) || secondStatus.contains(text("blockValid")), secondStatus)
        assert(isDisabled(buttonNamed(root, text("blockStop"))), "nothing to stop once found")
      }
    }
  }

  test("a hard search can be stopped; editing or locking also stops it") {
    val state = Var(initial)
    val locked = Var(false)
    mountedAsync(BlockchainEditor(pageInfo, state, initial, 4, locked.signal).getDomElement()) { root =>
      val mine = buttonNamed(blocks(root).head, text("blockMine"))
      click(mine)
      assert(isDisabled(mine), "a block is not mined twice at once")
      val stop = buttonNamed(root, text("blockStop"))
      assert(!isDisabled(stop))
      click(stop)
      assert(miningStatus(root).contains(text("miningStopped")))
      click(mine)
      typeInto(one(blocks(root)(1), "textarea"), "Bob pays Carol 3")
      assert(miningStatus(root).contains(text("miningStopped")), "editing stops mining")
      click(mine)
      locked.set(true)
      assert(miningStatus(root).contains(text("miningStopped")), "locking stops mining")
      assert(isDisabled(mine) && all(root, "textarea").forall(isDisabled))
      val nonce = state.now().blocks.head.nonce
      eventually(false, attempts = 10).recover(_ => ()).map { _ =>
        assertEquals(state.now().blocks.head.nonce, nonce, "a stopped search writes nothing later")
      }
    }
  }
}
