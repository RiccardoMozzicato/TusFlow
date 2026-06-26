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


    return (
        <div className="fixed inset-0 bg-on-background/40 backdrop-blur-[2px] flex items-center justify-center p-lg z-[9999]">
            <input ref={fileInputRef} type="file" accept="image/*" onChange={handleFileChange} style={{ display: "none" }} />

            <div className="bg-white w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
                <header className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
                    <div className="flex items-center gap-md">
                        <h3 className="font-headline-md text-headline-md text-primary">Edit Profile</h3>
                    </div>
                    <button
                        onClick={props.onClose}
                        className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-container-high transition-colors text-on-surface-variant cursor-pointer"
                    >
                        <span className="material-symbols-outlined">close</span>
                    </button>
                </header>

                <div className={`w-full flex-1 overflow-y-auto flex ${props.user?.roles.includes(ROLES.admin) ? 'flex-row' : 'flex-col'} items-center justify-center gap-8  pb-5 pt-5`}>

                    <div className="relative group">
                        <div className="w-40 h-40 rounded-full overflow-hidden border-4 border-surface-container shadow-sm bg-surface-container-low flex items-center justify-center">
                            { fileUrl || savedPhotoUrl ?
                                <img alt="Profile Preview" className="w-full h-full object-cover"
                                     src={ fileUrl || savedPhotoUrl || ""} />
                                :
                                    initialsOf(props.user?.fullName || "")
                            }
                        </div>
                    </div>

                    <div className="w-full max-w-[220px] flex flex-col gap-3">
                        <button
                            onClick={handleButtonClick}
                            disabled={isSaving}
                            className="w-full bg-ds-primary text-ds-on-primary h-12 rounded-xl font-body-md font-semibold hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed">
                            <span className="material-symbols-outlined text-[20px]">upload</span>
                            Upload New Photo
                        </button>
                        <button
                            onClick={removePhoto}
                            disabled={isSaving || (file == null && savedPhotoUrl == null)}
                            className="w-full bg-ds-error text-ds-on-primary h-10 rounded-xl font-body-md font-medium hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed">
                            <span className="material-symbols-outlined text-[20px]">delete</span>
                            Remove Photo
                        </button>
                    </div>

                </div>

                {props.user?.roles.includes(ROLES.admin) && (
                    <div className="w-full max-w-xs mx-auto mb-6 flex flex-col gap-1.5">

                        <label className="text-body-sm font-medium text-on-surface-variant px-1">Full Name</label>

                        <div className="relative flex items-center">
                            <span className="material-symbols-outlined absolute left-4 text-[20px] text-on-surface-variant pointer-events-none">
                                edit
                            </span>
                            <input
                                type="text"
                                value={newName}
                                onChange={(e) => setNewName(e.target.value)}
                                placeholder="Put your full name"
                                required={true}
                                className="w-full h-12 pl-11 pr-4 bg-surface-container-low border border-outline rounded-xl font-body-md text-on-surface outline-none transition-all focus:border-ds-primary focus:ring-2 focus:ring-ds-primary/20 placeholder:text-on-surface-variant/50"
                            />
                        </div>

                        <label className="text-body-sm font-medium text-on-surface-variant px-1">Email</label>

                        <div className="relative flex items-center">
                            <span className="material-symbols-outlined absolute left-4 text-[20px] text-on-surface-variant pointer-events-none">
                                edit
                            </span>
                            <input
                                type="email"
                                value={newEmail}
                                onChange={(e) => setNewEmail(e.target.value)}
                                placeholder="Put your email"
                                required={true}
                                className="w-full h-12 pl-11 pr-4 bg-surface-container-low border border-outline rounded-xl font-body-md text-on-surface outline-none transition-all focus:border-ds-primary focus:ring-2 focus:ring-ds-primary/20 placeholder:text-on-surface-variant/50"
                            />
                        </div>
                    </div>
                )}
                { error && <p className="mb-4 text-body-md text-ds-error">{error}</p> }


                <footer className="bg-surface-container-low p-lg border-t border-outline-variant flex justify-end gap-3">
                    <button
                        onClick={props.onClose}
                        disabled={isSaving}
                        className="w-32 h-12 border border-outline text-on-surface font-body-md font-semibold rounded-xl hover:bg-surface-container-high transition-colors disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={saveChanges}
                        className="w-32 h-12 bg-ds-primary text-ds-on-primary font-body-md font-semibold rounded-xl shadow-md hover:opacity-90 transition-opacity disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
                    >
                        {isSaving ? 'Saving…' : 'Save'}
                    </button>
                </footer>
            </div>
        </div> )
}