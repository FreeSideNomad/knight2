# Employee Portal - Quick Start Guide

## MVP Points 13-14: Employee Portal with Authentication

This module implements a Vaadin 24 employee portal with Azure AD/Entra ID authentication.

## Quick Start

### 1. Build the Module

From the project root:
```bash
mvn clean install -pl employee-portal -am
```

### 2. Run the Application

Development mode (with hot reload):
```bash
cd employee-portal
mvn spring-boot:run
```

The portal will start on **http://localhost:8081**

### 3. Access the Portal

- Open browser to http://localhost:8081
- You'll be redirected to the login page
- For MVP testing without Azure AD, the application shows "Guest User"

## Configuration for Azure AD

To enable Azure AD authentication, set these environment variables before running:

```bash
export AZURE_CLIENT_ID=your-azure-app-client-id
export AZURE_CLIENT_SECRET=your-azure-app-client-secret
export AZURE_ISSUER_URI=https://login.microsoftonline.com/{tenant-id}/v2.0
```

Or use application properties:
```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--AZURE_CLIENT_ID=xxx --AZURE_CLIENT_SECRET=yyy"
```

## Ports

- **Employee Portal**: http://localhost:8081
- **Backend API**: http://localhost:8080 (configured via API_URL env var)

## Features Implemented

### Authentication (Point 13)
- OAuth2/OIDC integration with Azure AD/Entra ID
- Security configuration using Spring Security
- Login and logout functionality
- User information display from OIDC claims

### User Interface (Point 14)
- Vaadin 24 AppLayout with header
- User name display in top-right corner
- Logout button
- Welcome page showing authenticated user info
- Responsive layout
- Navigation drawer (expandable for future features)

## Project Structure

```
employee-portal/
├── pom.xml                                  # Maven config with Vaadin 24
├── README.md                                # Full documentation
├── QUICKSTART.md                            # This file
├── .gitignore                               # Git ignore patterns
└── src/main/
    ├── java/com/knight/portal/
    │   ├── EmployeePortalApplication.java   # Main Spring Boot app
    │   ├── config/
    │   │   └── SecurityConfiguration.java   # OAuth2/OIDC security
    │   ├── security/
    │   │   └── AuthenticatedUser.java       # Current user service
    │   └── views/
    │       ├── MainLayout.java              # App layout with header & menu
    │       ├── HomeView.java                # Home/welcome page
    │       └── LoginView.java               # Login page
    └── resources/
        └── application.yml                   # App configuration
```

## Next Steps

1. **Azure AD Setup**: Register application in Azure Portal
2. **Configure Redirect URIs**: Add `http://localhost:8081/login/oauth2/code/azure`
3. **Set Environment Variables**: Configure client ID and secret
4. **Test Authentication**: Login with corporate credentials
5. **Add Features**: Implement additional views and integrations

## Testing Without Azure AD

For MVP testing, the application works without Azure AD configured:
- User shows as "Guest User"
- All protected routes are accessible
- Demonstrates UI layout and structure

## Troubleshooting

### Port 8081 Already in Use
Change the port in `application.yml`:
```yaml
server:
  port: 8082
```

### Vaadin Frontend Build Issues
Clean and rebuild:
```bash
mvn clean
mvn spring-boot:run
```

### Azure AD Login Fails
- Verify client ID and secret
- Check redirect URI matches Azure AD config
- Ensure tenant ID is correct
- Review application logs for detailed errors

## Related Documentation

- [README.md](README.md) - Full project documentation
- [Vaadin 24 Documentation](https://vaadin.com/docs/latest)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
