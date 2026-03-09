export const roleColors = (role: string): string => {
    switch(role.toLowerCase()) {
        case "admin":
        case "role_admin":
            return "primary";
        case "manager":
        case "role_manager":
            return "success";
        default:
            return "info";
    }
}