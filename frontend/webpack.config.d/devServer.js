// Webpack dev server configuration for Kotlin/JS frontend
// This file is auto-merged by Kotlin/JS Gradle plugin

if (config.devServer) {
    config.devServer.port = 3000;

    // Serve index.html and CSS from project root and resources
    config.devServer.static = [
        __dirname + '/..',           // frontend/ root (index.html)
        __dirname + '/../src/jsMain/resources'  // CSS files
    ];

    // Proxy API calls to backend server
    config.devServer.proxy = [
        {
            context: ['/api', '/health'],
            target: 'http://localhost:8080',
            changeOrigin: true
        }
    ];

    // SPA fallback — serve index.html for all routes
    config.devServer.historyApiFallback = true;
}
