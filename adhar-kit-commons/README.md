# Adhar Commons

This module provides common classes and utilities for the Adhar platform. It serves as a foundation for other modules and applications within the Adhar ecosystem.

## Features

### Base Classes

- **BaseController**: Common controller class with standardized response handling and exception handling
- **BaseService**: Common service class with CRUD operations and validation utilities
- **BaseClient**: Common REST client class with HTTP request/response handling

### Utility Classes

- **StringUtils**: Common string manipulation utilities
- **DateUtils**: Date and time formatting, parsing, and conversion utilities
- **CollectionUtils**: Collection manipulation utilities

### Common Models

- **ApiResponse**: Standard API response wrapper for REST endpoints
- **ErrorResponse**: Standard error response structure
- **PagedResult**: Generic container for paginated results

### Exception Handling

- **AdharException**: Base exception class for all Adhar platform exceptions
- **ResourceNotFoundException**: Exception for when a requested resource is not found
- **ValidationException**: Exception for validation errors
- **ServiceException**: Exception for service-level errors

### Constants and Enumerations

- **CommonConstants**: Common constants used across the platform
  - HTTP Headers
  - Content Types
  - Date/Time Formats
  - Response Status Values
  - Error Codes
  - MDC Keys for Logging

## Usage

To use this module, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.adhar</groupId>
    <artifactId>adhar-commons</artifactId>
</dependency>
```

## Examples

### Using ApiResponse

```java
// Success response with data
ApiResponse<User> response = ApiResponse.success(user);

// Success response with message and data
ApiResponse<List<User>> response = ApiResponse.success("Users retrieved successfully", users);

// Error response
ApiResponse<Void> response = ApiResponse.error("User not found");

// Error response with details
ErrorResponse errorDetails = ErrorResponse.of("USER_NOT_FOUND", "User with ID 123 not found");
ApiResponse<Void> response = ApiResponse.error("Failed to retrieve user", errorDetails);
```

### Using Exceptions

```java
// Resource not found
throw new ResourceNotFoundException("User", userId);

// Validation error
throw new ValidationException("Invalid user data")
    .addValidationError("email", "Email is required")
    .addValidationError("password", "Password must be at least 8 characters");

// Service error
try {
    // Some operation
} catch (Exception e) {
    throw new ServiceException("Failed to process user data", e);
}
```

### Using Utility Classes

```java
// StringUtils
String nullSafeString = StringUtils.defaultIfNull(nullableString);
boolean isEmpty = StringUtils.isEmpty(string);
String truncated = StringUtils.truncate(longString, 100);

// DateUtils
String formattedDate = DateUtils.formatDate(LocalDate.now());
LocalDate parsedDate = DateUtils.parseDate("2023-01-01");
long daysBetween = DateUtils.daysBetween(startDate, endDate);

// CollectionUtils
List<String> nonNullList = CollectionUtils.emptyIfNull(nullableList);
boolean isEmpty = CollectionUtils.isEmpty(collection);
List<Integer> evenNumbers = CollectionUtils.filter(numbers, n -> n % 2 == 0);
```
