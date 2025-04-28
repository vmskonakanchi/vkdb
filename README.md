# vkdb

![License](https://img.shields.io/badge/license-MIT-blue)
![Java](https://img.shields.io/badge/Java-21%2B-orange)

A lightweight, high-performance key-value store with notification capabilities, written in Java.

## Overview

vkdb is an in-memory key-value database server inspired by Redis. It demonstrates concurrent programming, networking, and event notification patterns in Java. Built using modern Java features like virtual threads, vkdb provides a simple yet powerful database for learning and experimentation.

## Key Features

- **✓ In-memory Key-Value Storage**: Fast data operations with O(1) average time complexity
- **✓ Concurrent Client Connections**: Handles multiple clients simultaneously with Java's virtual threads
- **✓ Real-time Notifications**: Subscribe to key changes with the `NOTIFY` command
- **✓ Simple Text Protocol**: Easy-to-use command interface similar to Redis
- **✓ Scalable Architecture**: Designed to handle thousands of concurrent connections
- **✓ Logging System**: Comprehensive logging for debugging and monitoring

## Quick Start

### Prerequisites

- Java 21 or higher (for virtual threads support)
- Maven

### Installation

```bash
# Clone the repository
git clone https://github.com/vmskonakanchi/vkdb.git
cd vkdb

# Build the project
mvn clean package
```

### Running the Server

```bash
java -jar server/target/vkdb-1.0.jar
```

### Running the Client

```bash
java -jar client/target/vkdb-client-1.0.jar
```

## Usage Examples

### Basic Operations

```bash
vkdb> SET user1 "John Doe"
SAVED
vkdb> SETX user1 "John Doe" 10000 # set key with with expiry
SAVED
vkdb> GET user1
John Doe
vkdb> DEL user1
DELETED
```

### Notification System

In Client 1

```shell
vkdb> NOTIFY user1
OK
```

In Client 2

```shell
vkdb> SET user1 "Jane Smith"
```

Client 1 will automatically receive:

```shell
CHANGED user1 Jane Smith
```

## Command Reference

| Command    | Format                            | Description                                                             |
|------------|-----------------------------------|-------------------------------------------------------------------------|
| SET        | `SET <KEY> <VALUE>`               | Sets a key to hold the specified value                                  |
| SETX       | `SETX <KEY> <VALUE> <TTL>`        | Sets a key to hold the specified value with expiry time in milliseconds |
| GET        | `GET <KEY>`                       | Gets the value of a key                                                 |
| DEL        | `DEL <KEY>`                       | Deletes a key                                                           |
| BEGIN      | `BEGIN`                           | Begins a transaction                                                    |
| COMMIT     | `COMMIT`                          | Commit a transaction                                                    |
| REGISTER   | `REGISTER <USERNAME> <PASSWORD>`  | Register a user with username and password                              |
| REGISTER   | `LOGIN <USERNAME> <PASSWORD>`     | Login a user with username and password                                 |
| WHOAMI     | `WHOAMI`                          | Gets the current logged in user                                         |
| NOTIFY     | `NOTIFY <KEY>`                    | Subscribe to changes for a specific key                                 |
| DISCONNECT | `DISCONNECT`                      | Closes the connection to the server                                     |

## Project Roadmap

- [x] **Persistence**: Save/restore data from disk
- [x] **Key Expiration**: Set time-to-live for keys
- [X] **Command Pipelining**: Batch multiple commands
- [ ] **Authentication**: Simple password protection
- [ ] **Clustering**: Distributed operation across multiple nodes
- [ ] **Data Types**: Support for lists, sets, and hashes
- [ ] **Admin Dashboard**: Web interface for monitoring and management

## Architecture

vkdb uses a client-server architecture with the following components:

- **Server**: Central component that accepts client connections
- **ClientHandler**: Manages individual client sessions and commands
- **NotifyItem**: Handles the notification system for key changes
- **SocketItem**: Encapsulates socket communication details
- **SaveItem**: Encapsulates data storage and expiration

The notification system is implemented using a publisher-subscriber pattern with a non-blocking queue for processing notifications asynchronously.

## Contributing

Contributions are welcome! Here are ways you can contribute:

- Implement / Suggest new features from the roadmap
- Improve performance or memory efficiency
- Add tests and documentation
- Report bugs and suggest enhancements

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgements

- Inspired by [Redis](https://redis.io/)
- Built with Java virtual threads (Project Loom)
