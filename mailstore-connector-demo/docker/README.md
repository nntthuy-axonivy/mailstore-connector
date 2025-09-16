## Mailserver Setup for Mailstore Connector Testing

To facilitate testing of the mailstore connector, follow the instructions below to start the mailserver and configure your environment.

### 1. Starting the Mailserver

Navigate to this directory in your terminal, then run:

```sh
docker compose up
```

### 2. SSL Certificate

An SSL certificate is already generated using the following command:

```sh
openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout ./config/ssl/mail.key \
  -out ./config/ssl/mail.crt \
  -subj "/CN=mail.marketplace.server.ivy-cloud.com"
```

**Note:**  
Replace `/CN=mail.marketplace.server.ivy-cloud.com` with your actual mail server FQDN if necessary.

### 3. Host Configuration

Add the following entry to your `hosts` file:

```
127.0.0.1   mail.marketplace.server.ivy-cloud.com
```

Replace `YOUR_SERVER_IP` with the appropriate server IP address.
127.0.0.1 is localhost ip

### 4. Test User Credentials

A test user has been pre-configured:

- **Username:** myuser@mail.marketplace.server.ivy-cloud.com
- **Password:** password123

You can use these credentials for mailstore connection and testing.

### 5. Adding Additional Users

To add more users, use the following command:

```sh
docker run --rm -v "$(pwd)/config":/tmp/docker-mailserver \
  -ti mailserver/docker-mailserver:latest \
  setup email add user1@test.local password123
```

Replace `user1@test.local` and `password123` with the desired email address and password.
