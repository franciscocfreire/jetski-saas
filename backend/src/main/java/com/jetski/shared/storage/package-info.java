/**
 * Storage API - Named Interface
 *
 * <p>Public API for file storage operations (S3, MinIO, Local).
 * This package is exposed as a named interface to allow other modules
 * to use storage services for managing photos and documents.
 *
 * <p><strong>Public API:</strong>
 * <ul>
 *   <li>{@link com.jetski.shared.storage.StorageService} - Main storage interface</li>
 *   <li>{@link com.jetski.shared.storage.PresignedUrl} - Presigned URL model</li>
 *   <li>{@link com.jetski.shared.storage.StorageMetadata} - Storage metadata</li>
 * </ul>
 *
 * <p><strong>Implementations (internal):</strong>
 * <ul>
 *   <li>LocalFileStorageService - Local filesystem storage</li>
 *   <li>MinIOStorageService - MinIO S3-compatible storage</li>
 *   <li>S3StorageService - Amazon S3 storage (future)</li>
 * </ul>
 *
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("storage")
package com.jetski.shared.storage;
