/*******************************************************************************
 * Copyright (c) 2015, 2016 Sebastian Stenzel and others.
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.filesystem.inmem;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.cryptomator.filesystem.WritableFile;
import org.cryptomator.io.ByteBuffers;

public class InMemoryWritableFile implements WritableFile {

	private final Consumer<Instant> lastModifiedSetter;
	private final Consumer<Instant> creationTimeSetter;
	private final Supplier<ByteBuffer> contentGetter;
	private final Consumer<ByteBuffer> contentSetter;
	private final Consumer<Void> deleter;
	private final WriteLock writeLock;

	private boolean open = true;
	private volatile int position = 0;

	public InMemoryWritableFile(Consumer<Instant> lastModifiedSetter, Consumer<Instant> creationTimeSetter, Supplier<ByteBuffer> contentGetter, Consumer<ByteBuffer> contentSetter, Consumer<Void> deleter,
			WriteLock writeLock) {
		this.lastModifiedSetter = lastModifiedSetter;
		this.contentGetter = contentGetter;
		this.contentSetter = contentSetter;
		this.deleter = deleter;
		this.writeLock = writeLock;
		this.creationTimeSetter = creationTimeSetter;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void moveTo(WritableFile other) throws UncheckedIOException {
		if (other instanceof InMemoryWritableFile) {
			InMemoryWritableFile destination = (InMemoryWritableFile) other;
			destination.contentSetter.accept(this.contentGetter.get());
			destination.contentGetter.get().rewind();
		}
		deleter.accept(null);
	}

	@Override
	public void setLastModified(Instant instant) throws UncheckedIOException {
		lastModifiedSetter.accept(instant);
	}

	@Override
	public void delete() throws UncheckedIOException {
		deleter.accept(null);
		open = false;
	}

	@Override
	public void truncate() throws UncheckedIOException {
		contentSetter.accept(ByteBuffer.allocate(0));
	}

	@Override
	public int write(ByteBuffer source) throws UncheckedIOException {
		ByteBuffer destination = contentGetter.get();
		int oldFileSize = destination.limit();
		int requiredSize = position + source.remaining();
		int newFileSize = Math.max(oldFileSize, requiredSize);
		if (destination.capacity() < requiredSize) {
			ByteBuffer old = destination;
			old.clear();
			int newBufferSize = Math.max(requiredSize, (int) (destination.capacity() * InMemoryFile.GROWTH_RATE));
			destination = ByteBuffer.allocate(newBufferSize);
			ByteBuffers.copy(old, destination);
			contentSetter.accept(destination);
		}
		destination.position(position);
		destination.limit(newFileSize);
		int numWritten = ByteBuffers.copy(source, destination);
		this.position += numWritten;
		return numWritten;
	}

	@Override
	public void position(long position) throws UncheckedIOException {
		assert position < Integer.MAX_VALUE : "Can not use that big in-memory files.";
		this.position = (int) position;
	}

	@Override
	public void close() throws UncheckedIOException {
		open = false;
		writeLock.unlock();
		lastModifiedSetter.accept(Instant.now());
	}

	@Override
	public void setCreationTime(Instant instant) throws UncheckedIOException {
		creationTimeSetter.accept(instant);
	}

}