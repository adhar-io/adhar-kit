# Contributing to Adhar Kit

First off, thank you for considering contributing to Adhar Kit! It's people like you that make Adhar Kit such a great tool for the enterprise Java community.

## 🌟 Vision

Adhar Kit is part of the **Adhar Platform** - a multi-framework enterprise toolkit committed to supporting all major Java frameworks as first-class citizens. We're building:

- ✅ **Spring Boot** (Production-ready)
- 🚧 **Quarkus** (In development - Q1 2025)
- 📅 **Micronaut** (Planned - Q3 2025)
- 📅 **Helidon** (Planned - Q4 2025)

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How Can I Contribute?](#how-can-i-contribute)
- [Multi-Framework Development](#multi-framework-development)
- [Development Process](#development-process)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## 📜 Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to conduct@adhar-platform.com.

### Our Pledge

- Be respectful and inclusive
- Welcome newcomers and help them learn
- Focus on what is best for the community
- Show empathy towards other community members

## 🚀 Getting Started

### Prerequisites

- **Java 25 LTS** (required)
- **Maven 3.8+** (required)
- **Git** (required)
- **IDE** (IntelliJ IDEA, Eclipse, or VS Code recommended)
- **Docker** (optional, for integration tests)

### Setting Up Development Environment

1. **Fork the repository**
   ```bash
   # Fork on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/adhar-kit.git
   cd adhar-kit
   ```

2. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/adhar-platform/adhar-kit.git
   ```

3. **Build the project**
   ```bash
   # Use the provided build script (suppresses Java 25 warnings)
   ./build.sh clean install
   
   # Or use Maven directly
   mvn clean install
   ```

4. **Run tests**
   ```bash
   mvn test
   ```

5. **Import into your IDE**
   - IntelliJ IDEA: File → Open → Select `pom.xml`
   - Eclipse: File → Import → Existing Maven Project
   - VS Code: Open folder, Java extension should detect Maven project

## 🤝 How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce**
- **Expected vs actual behavior**
- **Environment details** (Java version, OS, framework version)
- **Code samples** or **test cases** if applicable
- **Logs and stack traces**

**Bug Report Template:**
```markdown
**Description:**
A clear description of the bug.

**To Reproduce:**
1. Step 1
2. Step 2
3. ...

**Expected Behavior:**
What should happen.

**Actual Behavior:**
What actually happens.

**Environment:**
- Adhar Kit Version: 1.0.0
- Framework: Spring Boot 3.2.0
- Java Version: 25
- OS: macOS 14

**Logs:**
```
Paste relevant logs here
```
```

### Suggesting Enhancements

Enhancement suggestions are welcome! Please:

- Use a clear and descriptive title
- Provide detailed description of the enhancement
- Explain why this would be useful
- Provide examples if applicable
- Consider multi-framework impact

### Contributing Code

We love code contributions! Here are the areas where we need help:

#### Priority Areas for Contribution

**1. Framework Adapters (HIGH PRIORITY)**
- 🚧 Quarkus implementation (Q1 2025)
- 📅 Micronaut implementation (Q3 2025)
- 📅 Helidon implementation (Q4 2025)

**2. Core Modules Enhancement**
- Test coverage improvements (target: 90%+)
- Performance optimizations
- Documentation improvements
- Example applications

**3. New Features**
- AI/ML enhancements
- GraphQL support
- Advanced analytics
- Cloud provider integrations

**4. Bug Fixes**
- Check [GitHub Issues](https://github.com/adhar-platform/adhar-kit/issues)
- Look for `good-first-issue` label
- Look for `help-wanted` label

## 🔄 Multi-Framework Development

### Architecture Principles

When contributing, keep these principles in mind:

**1. Framework Parity**
- All frameworks must have 100% feature parity
- No framework is treated as second-class

**2. Framework-Agnostic Core**
- Shared logic goes in `adhar-kit-core`
- Framework-specific code in adapters

**3. Idiomatic Code**
- Spring Boot code should look like Spring Boot
- Quarkus code should look like Quarkus
- Follow each framework's conventions

**4. Consistent APIs**
- Same annotations and configuration across frameworks
- Easy migration between frameworks

### Module Structure

```
adhar-kit-<module>/
├── adhar-kit-<module>-api/          # Framework-agnostic interfaces
│   └── src/main/java/
│       └── com/adhar/kit/<module>/api/
├── adhar-kit-<module>-core/         # Shared implementation
│   └── src/main/java/
│       └── com/adhar/kit/<module>/core/
├── adhar-kit-<module>-spring/       # Spring Boot adapter
│   └── src/main/java/
│       └── com/adhar/kit/<module>/spring/
├── adhar-kit-<module>-quarkus/      # Quarkus adapter
│   └── src/main/java/
│       └── com/adhar/kit/<module>/quarkus/
└── adhar-kit-<module>-test/         # Common test utilities
    └── src/test/java/
```

### Implementing a Framework Adapter

**Step 1: Define API (Framework-Agnostic)**
```java
// adhar-kit-resilience-api
package com.adhar.kit.resilience.api;

public interface CircuitBreakerService {
    <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier);
    CircuitBreakerConfig getConfig(String name);
}
```

**Step 2: Implement Core Logic**
```java
// adhar-kit-resilience-core
package com.adhar.kit.resilience.core;

public abstract class AbstractCircuitBreakerService implements CircuitBreakerService {
    // Framework-agnostic implementation
    protected abstract CircuitBreakerRegistry getRegistry();
}
```

**Step 3: Spring Boot Adapter**
```java
// adhar-kit-resilience-spring
package com.adhar.kit.resilience.spring;

@Component
public class SpringCircuitBreakerService extends AbstractCircuitBreakerService {
    @Autowired
    private CircuitBreakerRegistry registry;
    
    @Override
    protected CircuitBreakerRegistry getRegistry() {
        return registry;
    }
}
```

**Step 4: Quarkus Adapter**
```java
// adhar-kit-resilience-quarkus
package com.adhar.kit.resilience.quarkus;

@ApplicationScoped
public class QuarkusCircuitBreakerService extends AbstractCircuitBreakerService {
    @Inject
    CircuitBreakerRegistry registry;
    
    @Override
    protected CircuitBreakerRegistry getRegistry() {
        return registry;
    }
}
```

## 💻 Development Process

### Branch Naming

- `feature/<feature-name>` - New features
- `bugfix/<bug-name>` - Bug fixes
- `framework/<framework-name>/<feature>` - Framework-specific work
- `docs/<doc-update>` - Documentation updates
- `refactor/<refactor-name>` - Code refactoring
- `test/<test-improvement>` - Test improvements

Examples:
- `feature/graphql-support`
- `framework/quarkus/resilience-module`
- `bugfix/circuit-breaker-timeout`
- `docs/quarkus-migration-guide`

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements

**Examples:**
```
feat(resilience): add rate limiting support

Implemented token bucket rate limiter with configurable
refill rate and capacity.

Closes #123
```

```
framework(quarkus): implement circuit breaker module

- Created Quarkus extension for circuit breaker
- Added CDI integration
- Implemented SmallRye Fault Tolerance support

Part of #456
```

### Development Workflow

1. **Create a branch**
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Make changes**
   - Write code
   - Add tests
   - Update documentation

3. **Test locally**
   ```bash
   ./build.sh clean install
   mvn verify
   ```

4. **Commit changes**
   ```bash
   git add .
   git commit -m "feat(module): description"
   ```

5. **Keep branch updated**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

6. **Push to your fork**
   ```bash
   git push origin feature/my-feature
   ```

7. **Create Pull Request**
   - Go to GitHub
   - Click "New Pull Request"
   - Fill in PR template

## 📝 Coding Standards

### Java Code Style

- **Java Version:** 25
- **Code Formatting:** Follow Google Java Style Guide
- **Line Length:** 120 characters max
- **Indentation:** 4 spaces (no tabs)

### Code Quality

- **No compiler warnings** - Code must compile without warnings
- **SonarQube compliant** - No critical/major issues
- **Null safety** - Use `Optional` where appropriate
- **Immutability** - Prefer immutable objects
- **Defensive copying** - When necessary

### Example Code Style

```java
package com.adhar.kit.module;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing user operations.
 *
 * <p>This service provides comprehensive user management capabilities including
 * creation, retrieval, update, and deletion operations with built-in validation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository repository;
    private final UserValidator validator;
    
    /**
     * Finds a user by email address.
     *
     * @param email the email address to search for (must not be null)
     * @return an Optional containing the user if found, empty otherwise
     * @throws IllegalArgumentException if email is null or invalid
     */
    public Optional<User> findByEmail(String email) {
        validator.validateEmail(email);
        return repository.findByEmail(email);
    }
}
```

### Documentation Requirements

**JavaDoc Required For:**
- All public classes
- All public methods
- All public constants
- Complex private methods

**JavaDoc Should Include:**
- Clear description
- `@param` for all parameters
- `@return` for return values
- `@throws` for exceptions
- `@since` for version added
- Code examples for complex APIs

## 🧪 Testing Guidelines

### Test Coverage Requirements

- **Unit Tests:** 80% minimum coverage
- **Integration Tests:** Critical paths covered
- **E2E Tests:** Happy path scenarios

### Test Structure

```java
@UnitTest
class UserServiceTest extends BaseUnitTest {
    
    @Mock
    private UserRepository repository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("Should find user by email when user exists")
    void shouldFindUserByEmail() {
        // Given
        String email = "test@example.com";
        User expectedUser = new User(email, "Test User");
        when(repository.findByEmail(email))
            .thenReturn(Optional.of(expectedUser));
        
        // When
        Optional<User> result = userService.findByEmail(email);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(email);
    }
}
```

### Test Naming

- Use descriptive test method names
- Follow pattern: `should<Expected>When<Condition>`
- Use `@DisplayName` for complex scenarios

### Framework-Specific Testing

**Spring Boot:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class ControllerTest {
    @Autowired
    private MockMvc mockMvc;
}
```

**Quarkus:**
```java
@QuarkusTest
class ResourceTest {
    @Inject
    UserService userService;
}
```

## 🔍 Pull Request Process

### Before Submitting

- [ ] Code compiles without warnings
- [ ] All tests pass (`mvn verify`)
- [ ] Code coverage meets requirements
- [ ] Documentation updated
- [ ] CHANGELOG.md updated (for features/fixes)
- [ ] No merge conflicts with main

### PR Template

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change (fix or feature)
- [ ] Documentation update
- [ ] Framework adapter implementation

## Framework Impact
- [ ] Spring Boot
- [ ] Quarkus
- [ ] Micronaut
- [ ] Framework-agnostic

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-reviewed code
- [ ] Commented complex code
- [ ] Updated documentation
- [ ] Added tests (coverage: XX%)
- [ ] All tests pass
- [ ] No breaking changes (or documented)

## Related Issues
Closes #XXX

## Screenshots (if applicable)
```

### Review Process

1. **Automated Checks**
   - CI build must pass
   - Code coverage must meet threshold
   - No security vulnerabilities

2. **Code Review**
   - At least 1 approval required
   - Framework experts review framework-specific code
   - Maintainers review breaking changes

3. **Merge**
   - Squash and merge (for clean history)
   - Delete branch after merge

## 👥 Community

### Communication Channels

- **GitHub Issues** - Bug reports, feature requests
- **GitHub Discussions** - Questions, ideas, community chat
- **Stack Overflow** - Tag: `adhar-kit`
- **Twitter** - [@AdharPlatform](https://twitter.com/AdharPlatform)
- **Email** - support@adhar-platform.com

### Getting Help

- Check [documentation](docs/)
- Search [existing issues](https://github.com/adhar-platform/adhar-kit/issues)
- Ask on [Discussions](https://github.com/adhar-platform/adhar-kit/discussions)
- Join our community chat

### Recognition

Contributors are recognized in:
- CHANGELOG.md for their contributions
- README.md contributors section
- Release notes
- Annual contributor awards

## 📜 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

## 🙏 Thank You!

Your contributions make Adhar Kit better for everyone. Whether it's:
- Reporting a bug
- Writing code
- Improving documentation
- Helping others
- Spreading the word

**Every contribution matters!** 

---

**Happy Contributing!** 🚀

*The Adhar Platform Team*

