# Trustify

Secure Community Marketplace Platform

---

## Overview

Trustify is a secure local marketplace designed to reduce scams in peer‑to‑peer buying, selling, and renting. The platform introduces identity verification, escrow‑style payments, and trust scoring to ensure safer transactions between strangers.

The goal is to simulate the reliability of real‑world commerce inside an online environment by enforcing accountability, verification, and transparent interaction.

---

## Problem Statement

Online community marketplaces frequently suffer from:

* Fake profiles and impersonation
* Payment fraud and chargebacks
* Lack of accountability between buyers and sellers
* No dispute tracking or moderation
* Unsafe off‑platform communication

Trustify addresses these issues by integrating authentication, verification, and transaction security directly into the platform workflow instead of relying on user trust alone.

---

## Key Features

* Secure authentication with JWT
* Role‑based authorization (User / Admin)
* Listing creation and management (Buy / Sell / Rent)
* Escrow‑based transaction workflow
* Stripe payment integration
* Real‑time buyer‑seller communication
* User trust and rating system
* Complaint and moderation system
* Identity verification support (CNIC + image verification – planned)

---

## System Architecture

Client (React) → REST API (Spring Boot) → Database (MongoDB Atlas)
↓
Stripe API
↓
Cloud Deployment (AWS EC2)

---

## Tech Stack

### Backend

* Java Spring Boot
* Spring Security
* JWT Authentication
* REST APIs
* MongoDB Atlas
* Stripe SDK
* Socket.IO (Chat Support)

### Frontend

* React.js
* Axios
* Context / State Management

### Cloud & DevOps

* AWS EC2 Deployment
* PM2 Process Manager
* Environment Variables Configuration

---

## How to Run Locally

### Prerequisites

* Java 17+
* Node.js 18+
* MongoDB Atlas Cluster
* Stripe Test Keys

---

### Backend Setup

1. Clone repository

```
git clone <repo-url>
cd trustify-backend
```

2. Configure environment variables (application.properties)

```
spring.data.mongodb.uri=YOUR_MONGO_URI
jwt.secret=YOUR_SECRET_KEY
stripe.secret.key=YOUR_STRIPE_SECRET
```

3. Build project

```
mvn clean install
```

4. Run application

```
java -jar target/trustify.jar
```

Server runs at:

```
http://localhost:8080/api
```

---

### Frontend Setup

1. Navigate to frontend

```
cd trustify-frontend
```

2. Install dependencies

```
npm install
```

3. Configure API URL

```
VITE_API_URL=http://localhost:8080/api
```

4. Start frontend

```
npm run dev
```

App runs at:

```
http://localhost:5173
```

---

## Deployment (Production)

### Backend (AWS EC2)

```
scp trustify.jar ec2-user@server-ip:/home/ec2-user
pm2 start "java -jar trustify.jar" --name trustify-backend
```

### Frontend

Deploy to Vercel / Netlify and set environment:

```
VITE_API_URL=http://your-server-ip:8080/api
```

---

## Future Improvements (FYP‑2)

* AI image fraud detection
* Identity verification system
* Admin dashboard
* Advanced trust scoring
* Full escrow lifecycle
* Monitoring & logging

---

## Project Goal

To provide a safer alternative to traditional community marketplaces by embedding trust directly into the system design rather than relying solely on user behavior.

---

## Author

Muhammad Suhaib
Software Developer
