package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.LiveFrame
import com.muvusoft.agentfarm.core.contract.Unwatch
import com.muvusoft.agentfarm.core.contract.Watch
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.live.Live
import com.muvusoft.agentfarm.core.live.LiveDepth
import com.muvusoft.agentfarm.core.live.LiveEvent
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile

/**
 * A screen's watch of one Console session, or of every card (`Live.EVERY_SESSION`, summary depth): asked when
 * collected, asked again at every welcome since a socket's watches end with it (the screen hears Resumed and
 * reads what it missed), ended with unwatch when the collector stops. Collect it only while the screen is in
 * view. The id is random: a closing old socket of this phone cannot end the new watch of the same name.
 */
fun ConnectionManager.watch(farmId: String, session: String, depth: LiveDepth): Flow<LiveEvent> = flow {
    val ask = Watch("w_" + UUID.randomUUID(), session, depth.wire)
    var endedByFarm = false
    // The socket the watch was asked on: its own welcome, still on its way through `frames`, is not a reconnect.
    var askedOn: String? = null
    emitAll(
        frames.onSubscription { if (send(farmId, ask)) askedOn = pageAccess(farmId)?.session }
            .filter { it.farm == farmId }
            .transformWhile { f ->
                when (val frame = f.frame) {
                    is Welcome -> {
                        if (frame.session == null || frame.session != askedOn) {
                            askedOn = frame.session
                            send(farmId, ask)
                            emit(LiveEvent.Resumed)
                        }
                        true
                    }
                    is LiveFrame -> {
                        if (frame.watch == ask.watch) Live.read(frame.data)?.let { emit(LiveEvent.Beat(frame.session, it)) }
                        true
                    }
                    is Unwatch -> if (frame.watch != ask.watch) true else {
                        endedByFarm = true
                        emit(LiveEvent.Ended(frame.reason))
                        false
                    }
                    else -> true
                }
            }
            .onCompletion { if (!endedByFarm) send(farmId, Unwatch(ask.watch)) },
    )
}
