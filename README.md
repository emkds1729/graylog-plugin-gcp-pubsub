# Graylog Google Cloud Pub/Sub Input Plugin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Graylog input plugin that enables receiving log messages from **Google Cloud Pub/Sub** subscriptions, pulling logs directly from GCP Cloud Logging via Pub/Sub.

## Overview

This plugin allows Graylog to consume logs from Google Cloud Platform services by:
- Connecting to GCP Pub/Sub subscriptions
- Pulling log messages in batches for efficient processing
- Processing messages in parallel for high throughput
- Automatically acknowledging successfully processed messages
- Sanitizing log payloads to ensure compatibility with Graylog

## Requirements

- **Graylog**: Version 6.1.5 or higher
- **Java**: Version 17 or higher
- **Google Cloud Platform**:
  - Active GCP project with Cloud Logging enabled
  - Pub/Sub topic configured to receive logs from Cloud Logging
  - Pub/Sub subscription created for the topic
  - Service account with Pub/Sub subscriber permissions

## Installation

### Download and Install

1. Download the latest plugin JAR from the [Releases](https://github.com/emkds1729/graylog-plugin-gcp-pubsub/releases) page

2. Copy the JAR file to your Graylog plugin directory:
   ```bash
   sudo cp graylog-plugin-gcp-pubsub-1.1.2.jar /usr/share/graylog-server/plugin/
   ```

3. Set appropriate permissions:
   ```bash
   sudo chown graylog:graylog /usr/share/graylog-server/plugin/graylog-plugin-gcp-pubsub-1.1.2.jar
   ```

4. Restart Graylog server:
   ```bash
   sudo systemctl restart graylog-server
   ```

## Google Cloud Platform Setup

### 1. Create a Pub/Sub Topic

```bash
gcloud pubsub topics create graylog-logs --project=YOUR_PROJECT_ID
```

### 2. Create a Log Sink

Route logs from Cloud Logging to your Pub/Sub topic:

```bash
gcloud logging sinks create graylog-sink \
  pubsub.googleapis.com/projects/YOUR_PROJECT_ID/topics/graylog-logs \
  --project=YOUR_PROJECT_ID \
  --log-filter='resource.type="gce_instance"'
```

Adjust the `--log-filter` to match your logging requirements. Examples:
- All logs: `--log-filter=''`
- Specific resource: `--log-filter='resource.type="k8s_container"'`
- Severity level: `--log-filter='severity>=ERROR'`

### 3. Create a Pub/Sub Subscription

```bash
gcloud pubsub subscriptions create graylog-pull-subscription \
  --topic=graylog-logs \
  --project=YOUR_PROJECT_ID \
  --ack-deadline=60
```

### 4. Create a Service Account

```bash
# Create service account
gcloud iam service-accounts create graylog-pubsub-reader \
  --display-name="Graylog Pub/Sub Reader" \
  --project=YOUR_PROJECT_ID

# Grant Pub/Sub Subscriber role
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:graylog-pubsub-reader@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/pubsub.subscriber"

# Create and download key
gcloud iam service-accounts keys create graylog-credentials.json \
  --iam-account=graylog-pubsub-reader@YOUR_PROJECT_ID.iam.gserviceaccount.com
```

### 5. Install Service Account Credentials on Graylog Server

```bash
sudo mkdir -p /var/lib/graylog-server
sudo cp graylog-credentials.json /var/lib/graylog-server/.graylog-pull-credentials.json
sudo chown graylog:graylog /var/lib/graylog-server/.graylog-pull-credentials.json
sudo chmod 600 /var/lib/graylog-server/.graylog-pull-credentials.json
```

**Important**: The credentials file **must** be located at `/var/lib/graylog-server/.graylog-pull-credentials.json`

## Configuration

### Add Input in Graylog

1. Log in to your Graylog web interface
2. Navigate to **System** → **Inputs**
3. Select **Google Cloud PubSub** from the input dropdown
4. Click **Launch new input**

### Input Configuration Fields

| Field | Description | Required | Default |
|-------|-------------|----------|---------|
| **GCP Project** | Your Google Cloud Platform project ID | Yes | `my-gcp-project` |
| **PubSub Subscription** | The name of your Pub/Sub subscription | Yes | `graylog-pull-subscription` |

Example configuration:
- **GCP Project**: `my-production-project`
- **PubSub Subscription**: `graylog-pull-subscription`

### Advanced Configuration

The plugin includes these built-in settings (not user-configurable):

- **Batch Size**: 1000 messages per pull request
- **Pull Timeout**: 19 seconds
- **Acknowledgment Deadline**: 60 seconds
- **Processing Interval**: 50ms between batches
- **Max Message Size**: 10MB

## Building from Source

### Prerequisites

- Maven 3.x
- Java 17
- Node.js and Yarn (for web interface)

### Build Steps

```bash
git clone https://github.com/emkds1729/graylog-plugin-gcp-pubsub.git
cd graylog-plugin-gcp-pubsub

# Build JAR
mvn clean package

# Build Debian package
mvn jdeb:jdeb

# Build RPM package
mvn rpm:rpm
```

Built artifacts will be in the `target/` directory.

## Development

For plugin development and testing:

```bash
# Run with hot reload for web interface
git clone https://github.com/Graylog2/graylog2-server.git
cd graylog2-server/graylog2-web-interface
ln -s /path/to/graylog-plugin-gcp-pubsub plugin/
npm install && npm start
```

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This plugin is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Support

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/emkds1729/graylog-plugin-gcp-pubsub/issues)
- **Discussions**: Join the conversation in [Graylog Community](https://community.graylog.org/)

## Acknowledgments

- Built using the [Graylog Plugin Framework](https://github.com/Graylog2/graylog2-server)
- Utilizes [Google Cloud Pub/Sub Java Client](https://cloud.google.com/pubsub/docs/reference/libraries#client-libraries-install-java)

## Author

Keith Sprochi <ksprochi@elementalmachines.com>

---

**Note**: This is a community-developed plugin and is not officially supported by Graylog, Inc.
