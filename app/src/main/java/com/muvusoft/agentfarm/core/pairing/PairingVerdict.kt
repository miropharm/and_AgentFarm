package com.muvusoft.agentfarm.core.pairing

import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.contract.PairRequest
import com.muvusoft.agentfarm.core.contract.PairResponse
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.contract.PairingInfo
import com.muvusoft.agentfarm.core.state.PairedFarm

/** Why a pairing did not happen; the screen prints `message`, never re-derives it. */
sealed class PairingProblem(val message: String) {
    object NotALink : PairingProblem("This is not an Agent Farm pairing link.")
    data class NewerContract(val v: Long) :
        PairingProblem("This farm speaks a newer contract (v$v); update the app.")
    object CodeUsed : PairingProblem("The pairing code was used or has expired; get a new code in Agent Farm.")
    object WrongFarm : PairingProblem("The farm that answered is not the farm in the link; nothing was paired.")
    object CertificateMismatch : PairingProblem("The server certificate does not match the fingerprint in the link; nothing was paired.")
    data class Unreachable(val tried: List<String>) :
        PairingProblem(
            "Could not reach the farm. Addresses tried: ${tried.joinToString(", ")}. " +
                "Is the phone on the same Wi-Fi? On the computer, Toolbox > Services > Agent Farm app says what stops it.",
        )
    data class Refused(val status: Int, val detail: String?) :
        PairingProblem("The farm refused the pairing ($status)" + (detail?.let { ": $it" } ?: "."))
}

sealed interface PairingStep {
    data class Ready(val info: PairingInfo) : PairingStep
    data class Rejected(val problem: PairingProblem) : PairingStep
}

object PairingVerdict {
    /** Whether a scanned or pasted link can be paired with by this build. */
    fun read(uri: String): PairingStep {
        val info = Pairing.parse(uri) ?: return PairingStep.Rejected(PairingProblem.NotALink)
        if (info.v > CONTRACT_VERSION) return PairingStep.Rejected(PairingProblem.NewerContract(info.v))
        return PairingStep.Ready(info)
    }

    fun request(info: PairingInfo, deviceName: String, publicKey: String, platform: String, app: String) =
        PairRequest(code = info.code, name = deviceName, publicKey = publicKey, platform = platform, app = app)

    /** The farm to remember; a response from another farm than the link named is a problem, not a farm. */
    fun accept(info: PairingInfo, response: PairResponse, keyAlias: String): Result {
        if (response.farm.id != info.farm) return Result.Failed(PairingProblem.WrongFarm)
        val farm = PairedFarm(
            id = info.farm,
            name = response.farm.name.ifBlank { info.name },
            device = response.device,
            scope = response.scope,
            fp = info.fp,
            addresses = info.addresses,
            key = keyAlias,
        )
        return Result.Paired(farm)
    }

    /** What an HTTP status from /pair means. */
    fun refused(status: Int, detail: String?): PairingProblem =
        if (status == 403) PairingProblem.CodeUsed else PairingProblem.Refused(status, detail)

    sealed interface Result {
        data class Paired(val farm: PairedFarm) : Result
        data class Failed(val problem: PairingProblem) : Result
    }
}
