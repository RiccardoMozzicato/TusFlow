package it.polito.wa2.userdetailservice.exceptions

class UserNotFoundException(
    override val message: String = "User not found"
) : RuntimeException()

class UserConflictError(
    override val message: String = "Conflict on user"
) : RuntimeException()

class SelfDeletionNotAllowedException(
    override val message: String = "Cannot delete your own user account"
) : RuntimeException()

class StorageQuotaBelowUsedException(
    override val message: String = "Storage quota cannot be set below current storage used"
) : RuntimeException()

class AvatarTypeUnsupported(
    override val message: String = "Unsupported media type"
): RuntimeException()
