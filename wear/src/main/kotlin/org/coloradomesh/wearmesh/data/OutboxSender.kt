package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.DirectOutcome
import java.util.concurrent.Executor

/**
 * Sends the store's waiting messages over one connection to the radio, and records how each
 * ended (M3 step 1). Live makes one per link, Check-in one per check-in.
 *
 * [sendPending] reads the messages still to send, on [writer], and hands each to the radio
 * ([Port]) once start-up is over. A channel message ends as [OutgoingStatus.SENT] when the radio
 * takes it; MeshCore reports nothing more for channel messages. A direct message is waited on for
 * its recipient's acknowledgement through every try the shared code makes, and ends as
 * [OutgoingStatus.DELIVERED] or [OutgoingStatus.UNCONFIRMED]. One the radio will not take ends as
 * [OutgoingStatus.FAILED], as does one that waited longer than [STALE_MS] for a connection: news
 * that late could mislead.
 *
 * At most [MAX_IN_FLIGHT] are with the radio at once, below the shared code's own limit of 5;
 * the rest go as earlier ones end. When the connection ends ([finish]), a channel message the
 * radio had not yet taken stays waiting for the next connection; a direct message already handed
 * over is recorded as not confirmed, since it may have gone out.
 *
 * The radio's answers come on the link's threads with the protocol client locked, so they only
 * update this object's state and queue writes; nothing here blocks on them.
 */
class OutboxSender(
    private val store: MessageStore,
    private val writer: Executor,
    private val radio: Port,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What this needs of the connection: the shared code's two send calls. */
    interface Port {
        fun sendChannelText(channelIndex: Int, text: String)

        /** Returns the id its outcome is reported under, or null when it cannot be queued now. */
        fun sendDirectText(publicKeyHex: String, text: String): Long?
    }

    private val lock = Any()

    /** Channel messages with the radio, in the order handed over: "slot|text" and the message. */
    private val channelsWaiting = ArrayList<Pair<String, OutgoingMessage>>()

    /** Direct messages with the radio, by the shared code's id. */
    private val directsWaiting = HashMap<Long, OutgoingMessage>()

    /** Row ids with the radio, so a second [sendPending] does not hand them over again. */
    private val inFlight = HashSet<Long>()

    /**
     * Messages this sender has ended, by row id and time written. Their new status is written
     * after the fact, so a read of the waiting messages may still list one; this keeps it from
     * being sent twice. A message tried again (Retry) is written anew, so it is not in here.
     */
    private val done = HashSet<String>()
    private val whenIdle = ArrayList<() -> Unit>()
    private var more = false
    private var finished = false

    /** Refills queued on [writer] and not yet run: the sender is not idle while one waits. */
    private var refills = 0

    /** Messages ended so far by this sender, for the log. */
    @Volatile
    var ended = 0
        private set

    /**
     * Hands the waiting messages to the radio; call once start-up is over (the first time the
     * radio's queue is empty), and again when a message is written. [then] runs once every
     * message handed over has ended, or the connection has.
     */
    fun sendPending(then: () -> Unit = {}) = sendPending(then, refill = false)

    private fun sendPending(then: () -> Unit, refill: Boolean) = writer.execute {
        val pending = try {
            store.pendingOutgoing()
        } catch (e: Exception) {
            log("Outbox: could not read the messages to send (${e.message})")
            emptyList()
        }
        val now = clock()
        val toSend = ArrayList<OutgoingMessage>()
        val over = synchronized(lock) {
            if (finished && refill) refills--
            finished
        }
        if (over) {
            then()
            return@execute
        }
        synchronized(lock) {
            if (refill) refills--
            for (message in pending) {
                if (message.id in inFlight || doneKey(message) in done) continue
                if (now - message.createdAt > STALE_MS) {
                    record(message, OutgoingStatus.FAILED, "not sent within ${STALE_MS / 60_000} min of writing it")
                    continue
                }
                if (inFlight.size + toSend.size >= MAX_IN_FLIGHT) {
                    more = true
                    break
                }
                toSend.add(message)
            }
            inFlight.addAll(toSend.map { it.id })
            whenIdle.add(then)
        }
        for (message in toSend) handOver(message)
        runIfIdle()
    }

    private fun handOver(message: OutgoingMessage) {
        val conversation = message.conversation
        when {
            conversation.startsWith("c:") -> {
                val slot = conversation.removePrefix("c:").toIntOrNull()
                if (slot == null) {
                    end(message, OutgoingStatus.FAILED, "no such channel")
                    return
                }
                synchronized(lock) { channelsWaiting.add(key(slot, message.body) to message) }
                // The answer may come at once, inside this call, when the radio cannot take it.
                radio.sendChannelText(slot, message.body)
            }
            conversation.startsWith("d:") -> {
                val id = radio.sendDirectText(conversation.removePrefix("d:"), message.body)
                if (id == null) {
                    // Too many waiting, or the link not ready: it stays waiting for later.
                    synchronized(lock) {
                        inFlight.remove(message.id)
                        more = true
                    }
                } else {
                    val late = synchronized(lock) {
                        if (!finished) directsWaiting[id] = message
                        finished
                    }
                    // The connection ended while the radio was taking it: it may have gone out.
                    if (late) record(message, OutgoingStatus.UNCONFIRMED, "the connection ended before an acknowledgement")
                }
            }
            else -> end(message, OutgoingStatus.FAILED, "not a conversation that can be sent to")
        }
    }

    /** From the protocol client: the radio took, or did not take, a channel message. */
    fun onSendResult(channelIndex: Int, text: String, accepted: Boolean) {
        val message = synchronized(lock) {
            val at = channelsWaiting.indexOfFirst { it.first == key(channelIndex, text) }
            if (at < 0) return
            channelsWaiting.removeAt(at).second
        }
        if (accepted) end(message, OutgoingStatus.SENT, null) else end(message, OutgoingStatus.FAILED, "the radio would not send it")
    }

    /** From the protocol client: how a direct message ended. */
    fun onDirectResult(id: Long, outcome: DirectOutcome) {
        val message = synchronized(lock) { directsWaiting.remove(id) } ?: return
        when (outcome) {
            DirectOutcome.DELIVERED -> end(message, OutgoingStatus.DELIVERED, null)
            DirectOutcome.UNCONFIRMED -> end(message, OutgoingStatus.UNCONFIRMED, "no acknowledgement after every try")
            // Most often a contact the radio has dropped (its list is full) or never had.
            DirectOutcome.REFUSED -> end(message, OutgoingStatus.FAILED, "the radio would not send it", checkContact = true)
        }
    }

    /**
     * The connection has ended. Channel messages the radio had not taken stay waiting; direct
     * messages handed over are recorded as not confirmed.
     */
    fun finish() {
        val unconfirmed: List<OutgoingMessage>
        synchronized(lock) {
            if (finished) return
            finished = true
            unconfirmed = directsWaiting.values.toList()
            directsWaiting.clear()
            channelsWaiting.clear()
            inFlight.clear()
            more = false
        }
        for (message in unconfirmed) record(message, OutgoingStatus.UNCONFIRMED, "the connection ended before an acknowledgement")
        runWaiters()
    }

    /** Whether any message is with the radio now. */
    fun busy(): Boolean = synchronized(lock) { inFlight.isNotEmpty() }

    private fun end(message: OutgoingMessage, status: OutgoingStatus, why: String?, checkContact: Boolean = false) {
        synchronized(lock) { inFlight.remove(message.id) }
        record(message, status, why, checkContact)
        val again = synchronized(lock) {
            (more && !finished).also {
                if (it) {
                    more = false
                    refills++
                }
            }
        }
        if (again) sendPending({}, refill = true)
        runIfIdle()
    }

    /**
     * Records how [message] ended, on [writer]. With [checkContact], a refused direct message to a
     * node the store has no contact for is [OutgoingStatus.NO_CONTACT]: the store holds what the
     * radio last listed.
     */
    private fun record(message: OutgoingMessage, status: OutgoingStatus, why: String?, checkContact: Boolean = false) {
        synchronized(lock) { done.add(doneKey(message)) }
        ended++
        val direct = message.conversation.startsWith("d:")
        val what = if (direct) "Direct message to ${message.conversation.removePrefix("d:")}" else
            "Message on channel ${message.conversation.removePrefix("c:")}"
        writer.execute {
            try {
                val missing = checkContact && direct && !store.hasContact(message.conversation.removePrefix("d:"))
                val final = if (missing) OutgoingStatus.NO_CONTACT else status
                val reason = if (missing) "your radio does not have this contact (its list may be full)" else why
                log("Outbox: $what ${describe(final)}" + (reason?.let { ": $it" } ?: ""))
                store.setOutgoingStatus(message.id, final)
            } catch (e: Exception) {
                log("Outbox: could not record a message's status (${e.message})")
            }
        }
    }

    private fun doneKey(message: OutgoingMessage) = "${message.id}@${message.createdAt}"

    private fun runIfIdle() {
        if (synchronized(lock) { inFlight.isEmpty() && refills == 0 }) runWaiters()
    }

    private fun runWaiters() {
        val waiting = synchronized(lock) { whenIdle.toList().also { whenIdle.clear() } }
        waiting.forEach { it() }
    }

    private fun key(slot: Int, text: String) = "$slot|$text"

    private fun describe(status: OutgoingStatus) = when (status) {
        OutgoingStatus.SENDING -> "waiting"
        OutgoingStatus.SENT -> "sent"
        OutgoingStatus.DELIVERED -> "delivered"
        OutgoingStatus.UNCONFIRMED -> "not confirmed"
        OutgoingStatus.FAILED, OutgoingStatus.NO_CONTACT -> "failed"
    }

    companion object {
        /** A message not sent this long after it was written is given up on. */
        const val STALE_MS = 30 * 60_000L

        /** Below the shared code's limit of 5 queued, so it never refuses one for being full. */
        const val MAX_IN_FLIGHT = 4
    }
}
