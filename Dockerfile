# Node.js Server Dockerfile
FROM node:18-alpine

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install --production

# Copy server source code (exclude Android app)
COPY app.js ./
COPY routes/ ./routes/
COPY services/ ./services/

# Expose port
EXPOSE 3000

# Start the server
CMD ["npm", "start"]