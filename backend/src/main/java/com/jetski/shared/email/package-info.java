/**
 * Email API - Public interface for sending emails.
 *
 * <p>This package defines the public email contract of the shared module:
 * <ul>
 *   <li>{@link EmailService} - Service for sending various types of emails</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * This is a named interface of the 'shared' module. Other modules can depend on
 * this service to send emails without breaking modularity rules.
 *
 * @since 0.4.0
 */
@org.springframework.modulith.NamedInterface("email")
package com.jetski.shared.email;
