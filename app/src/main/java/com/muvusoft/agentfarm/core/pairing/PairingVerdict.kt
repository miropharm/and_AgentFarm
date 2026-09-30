package com.muvusoft.agentfarm.core.pairing

import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.contract.PairRequest
import com.muvusoft.agentfarm.core.contract.PairResponse
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.contract.PairingInfo
import com.muvusoft.agentfarm.core.state.PairedFarm

/** Why a pairing did not happen; the screen prints `message`, never re-derives it. */
sealed class PairingProblem(val message: String) {
    object NotALink : PairingProblem("Bu bir Agent Farm eşleşme bağlantısı değil.")
    data class NewerContract(val v: Long) :
        PairingProblem("Bu çiftlik daha yeni bir sözleşme konuşuyor (v$v); uygulamayı güncelleyin.")
    object CodeUsed : PairingProblem("Eşleşme kodu kullanılmış ya da süresi dolmuş; Agent Farm'da yeni kod alın.")
    object WrongFarm : PairingProblem("Yanıt veren çiftlik, bağlantıdaki çiftlik değil; eşleşme yapılmadı.")
    object CertificateMismatch : PairingProblem("Sunucu sertifikası bağlantıdaki parmak iziyle uyuşmuyor; eşleşme yapılmadı.")
    data class Unreachable(val tried: List<String>) :
        PairingProblem("Çiftliğe ulaşılamadı. Denenen adresler: ${tried.joinToString(", ")}")
    data class Refused(val status: Int, val detail: String?) :
        PairingProblem("Çiftlik eşleşmeyi reddetti ($status)" + (detail?.let { ": $it" } ?: "."))
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
