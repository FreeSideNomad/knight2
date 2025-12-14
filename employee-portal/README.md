# Knight Employee Portal

Vaadin 24-based employee portal with Azure AD/Entra ID authentication.

## Overview

The Employee Portal is a web-based UI for employees to access Knight platform services. It uses:
- **Vaadin 24** for the frontend framework
- **Spring Boot 3.x** for the backend
- **OAuth2/OIDC** for authentication with Azure AD/Entra ID
- **Spring Security** for security configuration

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.x
- Azure AD/Entra ID application registration (for production)

### Development Mode

Run the application in development mode (with hot reload):

```bash
cd employee-portal
mvn spring-boot:run
```

The portal will be available at: http://localhost:8081

### Production Build

Build for production with optimized Vaadin bundle:

```bash
mvn clean package -Pproduction
java -jar target/employee-portal-0.1.0-SNAPSHOT.jar
```

## Configuration

### Environment Variables

Configure Azure AD authentication using these environment variables:

```bash
# Azure AD Client ID (Application ID)
export AZURE_CLIENT_ID=your-client-id

# Azure AD Client Secret
export AZURE_CLIENT_SECRET=your-client-secret

# Azure AD Tenant-specific issuer URI (optional, defaults to common)
export AZURE_ISSUER_URI=https://login.microsoftonline.com/{tenant-id}/v2.0

# Backend API URL (optional, defaults to http://localhost:8080)
export API_URL=http://localhost:8080
```

### Azure AD Application Registration

To set up Azure AD authentication:

1. Register an application in Azure Portal
2. Add redirect URI: `http://localhost:8081/login/oauth2/code/azure`
3. Configure API permissions: `openid`, `profile`, `email`
4. Create a client secret
5. Set the environment variables above

### Application Configuration

The main configuration is in `src/main/resources/application.yml`:

- **Server Port**: 8081 (to avoid conflict with backend API on 8080)
- **OAuth2 Settings**: Azure AD configuration
- **API Backend**: URL for backend services
- **Vaadin Settings**: Framework configuration

## Project Structure

```
employee-portal/
├── pom.xml                          # Maven configuration with Vaadin dependencies
└── src/main/
    ├── java/com/knight/portal/
    │   ├── EmployeePortalApplication.java    # Main Spring Boot application
    │   ├── config/
    │   │   └── SecurityConfiguration.java    # OAuth2/OIDC security setup
    │   ├── security/
    │   │   └── AuthenticatedUser.java        # Service to access current user
    │   ├── views/
    │   │   ├── MainLayout.java               # Main app layout with header and menu
    │   │   ├── HomeView.java                 # Home page view
    │   │   └── LoginView.java                # Login page view
    │   └── services/                         # Services (to be added)
    └── resources/
        ├── application.yml                   # Application configuration
        └── META-INF/resources/               # Static resources
```

## Features

### MVP Features (Current)
- OAuth2/OIDC authentication with Azure AD
- User information display (name, email)
- Logout functionality
- Responsive layout with Vaadin AppLayout
- Development and production build profiles

### Future Enhancements
- Navigation menu with multiple views
- Integration with backend API services
- Client profile management
- Approval workflows
- Dashboard and reporting

## Security

- All routes except `/login` require authentication
- OAuth2 authorization code flow with PKCE
- OIDC logout support (logs out from Azure AD)
- Session management via Spring Security

## Development

### Hot Reload

Vaadin supports hot reload in development mode. Changes to Java files will trigger automatic recompilation and browser refresh.

### Styling

The portal uses Vaadin's Lumo theme. Custom styling can be added in:
- `frontend/themes/employee-portal/`

### Adding New Views

1. Create a new class extending `VerticalLayout` or other Vaadin component
2. Annotate with `@Route(value = "path", layout = MainLayout.class)`
3. Add `@PageTitle` for the browser tab title
4. Use `@PermitAll` or other security annotations

Example:
```java
@Route(value = "clients", layout = MainLayout.class)
@PageTitle("Clients | Knight Employee Portal")
@PermitAll
public class ClientsView extends VerticalLayout {
    // View implementation
}
```

## Troubleshooting

### Port Already in Use
If port 8081 is already in use, change it in `application.yml`:
```yaml
server:
  port: 8082
```

### Azure AD Authentication Issues
- Verify client ID and secret are correct
- Check redirect URI matches Azure AD configuration
- Ensure tenant ID is correct (or use "common" for multi-tenant)
- Review logs for detailed error messages

### Vaadin Development Mode
If frontend changes aren't reflected:
1. Stop the application
2. Run `mvn clean`
3. Start again with `mvn spring-boot:run`

## Related Modules

- **Backend API**: `application/` module (runs on port 8080)
- **Domain Layer**: `domain/` modules
- **Kernel**: `kernel/` shared utilities

## License

Internal Knight platform project.
