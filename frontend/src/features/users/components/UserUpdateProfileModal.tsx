import type {UserInfo} from "@/features/auth/types.ts";
import {useRef, useState} from "react";
import {
    deletePhotoProfile,
    getCurrentUser,
    updatePhotoProfile,
    updateUser,
    type UserDTO
} from "@/features/users/usersApi.ts";
import {loadCurrentUser} from "@/features/auth/authSlice.ts";
import {useAppDispatch} from "@/app/hooks.ts";
import {initialsOf} from "@/lib/initials.ts";
import {ROLES} from "@/features/profile/roles.ts";

interface UserProfileModalInterface {
    user: UserInfo | null,
    onClose: () => void,
    updateUser: () => void
}

export default function UserProfileModal(props: UserProfileModalInterface) {
    const dispatch = useAppDispatch()
    const [file, setFile] = useState<File | null>(null)
    const [fileUrl, setFileUrl] = useState<string | null>(null)
    const [newName, setNewName] = useState<string | undefined>(props.user?.fullName)
    const [newEmail, setNewEmail] = useState<string | undefined>(props.user?.email)
    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const fileInputRef = useRef<HTMLInputElement>(null);

    // The photo currently saved on the server, used as the modal's preview when
    // no new file has been picked.
    const savedPhotoUrl = props.user?.photoProfileUrl ?? null

    const handleButtonClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = (
        event: React.ChangeEvent<HTMLInputElement>
    ) => {
        console.log("Changed");
        const file = event.target.files?.[0] ?? null
        setFile(file)
        if(file != null) setFileUrl(URL.createObjectURL(file))
    };

    const savePhoto = async () => {
        if (file == null || props.user == null) return
        setIsSaving(true)
        setError(null)
        try {
            await updatePhotoProfile(file)
            // Refresh Redux so the new avatar shows everywhere (profile + sidebar).
            await dispatch(loadCurrentUser())
            props.onClose()
        } catch {
            setError('Could not save the photo. Please try again.')
        } finally {
            setIsSaving(false)
        }
    }

    const removePhoto = async () => {
        // If the user only picked a not-yet-saved file, just clear the local preview.
        if (fileUrl) URL.revokeObjectURL(fileUrl);
        if (file != null) {
            setFile(null)
            setFileUrl(null)
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
            return
        }
        if (savedPhotoUrl == null) return
        setIsSaving(true)
        setError(null)
        try {
            await deletePhotoProfile()
            await dispatch(loadCurrentUser())
            props.onClose()
        } catch {
            setError('Could not remove the photo. Please try again.')
        } finally {
            setIsSaving(false)
        }
    }

    const updateProfileInformation = async () => {
        if(!props.user || newName == undefined || newEmail == undefined) return
        try {
            console.log("Provo a salvare")
            const currentUser = await getCurrentUser()
            console.log("Current user: ", currentUser)
            const updatedCurrentUser: UserDTO = {...currentUser, fullName: newName, email: newEmail}
            console.log("Updated user: ", updatedCurrentUser)
            await updateUser(updatedCurrentUser)
            await dispatch(loadCurrentUser())
            props.onClose()
        } catch(err: any) {
            setError(err)
        } finally {
            setIsSaving(false)
        }
    }

    const saveChanges = async () => {
        if(isSaveDisabled()) return
        await savePhoto()

        if(props.user?.roles.includes(ROLES.admin) && (newName != props.user?.fullName || newEmail != props.user?.email)) {
            await updateProfileInformation()
        }

        props.updateUser()
    }

    const isSaveDisabled = () => {
        if(isSaving) return true
        if(props.user?.roles.includes(ROLES.admin)) {
            if(file != null) return false;
            console.log("File nullo");
            if(newName !== undefined && newName.trim() === "" && newName.trim() != props.user?.fullName) return false;
            console.log("Nome uguale");
            if(newEmail !== undefined && newEmail.trim() === "" && newEmail.trim() != props.user?.email) return false;
            console.log("Email uguale")
        } else {
            return file == null
        }

        return false
    }


    const isAdmin = props.user?.roles.includes(ROLES.admin) ?? false
    const hasPhoto = file != null || savedPhotoUrl != null

    return (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-ds-on-background/40 p-margin-mobile backdrop-blur-[4px]">
            <input ref={fileInputRef} type="file" accept="image/*" onChange={handleFileChange} style={{ display: "none" }} />

            <div className="flex max-h-[90vh] w-full max-w-[440px] flex-col overflow-hidden rounded-xl border border-ds-outline-variant/30 bg-ds-surface-container-lowest shadow-xl">
                {/* Header */}
                <header className="flex items-center justify-between border-b border-ds-outline-variant/20 bg-ds-surface px-6 py-4">
                    <h3 className="font-ds-heading text-ds-headline-sm text-ds-primary">Upload Profile Picture</h3>
                    <button
                        onClick={props.onClose}
                        aria-label="Close modal"
                        className="flex h-10 w-10 items-center justify-center rounded-full text-ds-on-surface-variant transition-colors hover:bg-ds-surface-container-high disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                        disabled={isSaving}
                    >
                        <span className="material-symbols-outlined">close</span>
                    </button>
                </header>

                {/* Body */}
                <div className="flex flex-col items-center gap-6 overflow-y-auto p-6 text-center">
                    {/* Avatar preview */}
                    <div className="relative">
                        <div className="flex h-44 w-44 items-center justify-center overflow-hidden rounded-full border-4 border-ds-surface-container-high bg-ds-surface-container font-ds-heading text-ds-display-lg text-ds-on-surface-variant shadow-[0_0_20px_rgba(2,36,72,0.08)]">
                            { fileUrl || savedPhotoUrl ?
                                <img alt="Profile Preview" className="h-full w-full object-cover"
                                     src={ fileUrl || savedPhotoUrl || ""} />
                                :
                                    initialsOf(props.user?.fullName || "")
                            }
                        </div>
                        <div className="absolute bottom-2 right-2 flex items-center justify-center rounded-full border-2 border-ds-surface-container-lowest bg-ds-primary p-2.5 text-ds-on-primary shadow-lg">
                            <span className="material-symbols-outlined text-[20px]">photo_camera</span>
                        </div>
                    </div>

                    {/* Description & hint */}
                    <div className="flex flex-col gap-1">
                        <p className="max-w-[320px] font-ds-body text-ds-body-md text-ds-on-surface-variant">
                            Personalize your account. This photo will be visible to your team and collaborators.
                        </p>
                        <p className="pt-2 font-ds-label text-ds-label-md uppercase tracking-widest text-ds-secondary">
                            Supported: JPG, PNG • Max size: 5MB
                        </p>
                    </div>

                    {/* Admin-only profile fields */}
                    {isAdmin && (
                        <div className="flex w-full max-w-xs flex-col gap-1.5 text-left">
                            <label className="px-1 font-ds-body text-ds-body-sm text-ds-on-surface-variant">Full Name</label>
                            <div className="relative flex items-center">
                                <span className="material-symbols-outlined pointer-events-none absolute left-4 text-[20px] text-ds-on-surface-variant">edit</span>
                                <input
                                    type="text"
                                    value={newName}
                                    onChange={(e) => setNewName(e.target.value)}
                                    placeholder="Put your full name"
                                    required
                                    className="h-12 w-full rounded-xl border border-ds-outline bg-ds-surface-container-low pl-11 pr-4 font-ds-body text-ds-body-md text-ds-on-surface outline-none transition-all placeholder:text-ds-on-surface-variant/50 focus:border-ds-primary focus:ring-2 focus:ring-ds-primary/20"
                                />
                            </div>
                            <label className="px-1 font-ds-body text-ds-body-sm text-ds-on-surface-variant">Email</label>
                            <div className="relative flex items-center">
                                <span className="material-symbols-outlined pointer-events-none absolute left-4 text-[20px] text-ds-on-surface-variant">edit</span>
                                <input
                                    type="email"
                                    value={newEmail}
                                    onChange={(e) => setNewEmail(e.target.value)}
                                    placeholder="Put your email"
                                    required
                                    className="h-12 w-full rounded-xl border border-ds-outline bg-ds-surface-container-low pl-11 pr-4 font-ds-body text-ds-body-md text-ds-on-surface outline-none transition-all placeholder:text-ds-on-surface-variant/50 focus:border-ds-primary focus:ring-2 focus:ring-ds-primary/20"
                                />
                            </div>
                        </div>
                    )}

                    {error && <p className="font-ds-body text-ds-body-md text-ds-error">{error}</p>}

                    {/* Action buttons */}
                    <div className="flex w-full flex-col gap-3 pt-2">
                        <button
                            onClick={handleButtonClick}
                            disabled={isSaving}
                            className="flex w-full items-center justify-center gap-2 rounded-lg bg-ds-primary px-6 py-3 font-ds-body text-ds-body-md font-bold text-ds-on-primary shadow-sm transition-all hover:bg-ds-primary-container active:scale-[0.98] disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed">
                            <span className="material-symbols-outlined">upload</span>
                            Upload New Photo
                        </button>
                        <button
                            onClick={removePhoto}
                            disabled={isSaving || !hasPhoto}
                            className="flex w-full items-center justify-center gap-2 rounded-lg border border-transparent px-6 py-3 font-ds-body text-ds-body-md font-medium text-ds-error transition-all hover:border-ds-error/20 hover:bg-ds-error-container/20 active:scale-[0.98] disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed">
                            <span className="material-symbols-outlined">delete</span>
                            Remove Photo
                        </button>
                    </div>
                </div>

                {/* Footer: Save / Cancel */}
                <footer className="flex justify-end gap-3 border-t border-ds-outline-variant bg-ds-surface-container-low p-6">
                    <button
                        onClick={props.onClose}
                        disabled={isSaving}
                        className="h-12 w-32 rounded-xl border border-ds-outline font-ds-body text-ds-body-md font-semibold text-ds-on-surface transition-colors hover:bg-ds-surface-container-high disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={saveChanges}
                        disabled={isSaveDisabled()}
                        className="h-12 w-32 rounded-xl bg-ds-primary font-ds-body text-ds-body-md font-semibold text-ds-on-primary shadow-md transition-opacity hover:opacity-90 disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                    >
                        {isSaving ? 'Saving…' : 'Save'}
                    </button>
                </footer>

                {/* Subtle branding strip */}
                <div className="flex justify-center border-t border-ds-outline-variant/10 bg-ds-surface-container-low px-6 py-3">
                    <div className="flex items-center gap-1 opacity-40">
                        <span className="material-symbols-outlined text-[14px]">verified_user</span>
                        <span className="font-ds-label text-ds-label-sm">Secure storage powered by DocPortal</span>
                    </div>
                </div>
            </div>
        </div> )
}