# 🏦 Financial Advisor AI Agent

## ✨ Project Overview

This repository contains the source code for the **Financial Advisor AI Agent**, a backend service built using **Spring Boot 3**.


### 🚀 Live Deployment (Render)

The latest version of the API is automatically built and deployed on Render.

**Access the live service here:**
[**https://financial-advisor-bx9s.onrender.com/**](https://financial-advisor-bx9s.onrender.com/)

_**Note:** This service is running on the free tier, which means it may take 30-60 seconds to respond to the very first request after a period of inactivity (a "cold start")._

---

## 🛠️ Technology Stack

| Category | Technology | Version / Tool |
| :--- | :--- | :--- |
| **Backend Language** | Java | JDK 17+ |
| **Framework** | Spring Boot | 3.x |
| **Build Tool** | Maven | 3.x |
| **Containerization** | Docker | Latest |
| **Cloud Deployment** | Render | Web Service |

---

## ⚙️ Local Development

Follow these steps to set up and run the application on your local machine.

### Prerequisites

* Java Development Kit (JDK) 17 or higher
* Apache Maven 3.6 or higher
* Git

### Steps

1.  **Clone the Repository**
    ```bash
    git clone <YOUR_REPOSITORY_URL>
    cd financial-advisor
    ```

2.  **Build the Project**
    Use Maven to clean the project and build the executable JAR file.
    ```bash
    mvn clean package -DskipTests
    ```
    This will generate the fat JAR in the `target/` directory, typically named `financial-advisor-0.0.1-SNAPSHOT.jar`.

3.  **Run the Application**
    Execute the JAR file using the Java runtime.
    ```bash
    java -jar target/financial-advisor-0.0.1-SNAPSHOT.jar
    ```

The API will now be accessible at `http://localhost:8080`.

---

## 🐳 Docker Deployment

The project includes a `Dockerfile` for streamlined, consistent deployment.

### 1. Build the Docker Image
```bash
docker build -t financial-advisor-api:latest .
