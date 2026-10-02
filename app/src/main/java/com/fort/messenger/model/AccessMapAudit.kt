package com.fort.messenger.model

data class EnclaveDevice(
    val id: String,
    val deviceName: String,
    val securityLevel: String, // e.g. "StrongBox Keymaster v4"
    val enrolledDate: String,
    val isCurrentDevice: Boolean
)

data class ActivePassLink(
    val id: String,
    val name: String,
    val cardType: CardType,
    val durationType: PassDurationType,
    val creationDate: String,
    val timesUsed: Int
)

data class BackupVaultStatus(
    val isEncrypted: Boolean,
    val algorithm: String, // "XChaCha20-Poly1305"
    val lastBackupDate: String,
    val seedPhraseVerified: Boolean
)

data class AccessMapAudit(
    val activePassesCount: Int,
    val enclaveDevices: List<EnclaveDevice>,
    val activePassLinks: List<ActivePassLink>,
    val vaultStatus: BackupVaultStatus
)
