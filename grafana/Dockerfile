FROM grafana/grafana:latest

# Install the Sentry datasource plugin
RUN grafana-cli plugins install grafana-sentry-datasource

# Install the Google Analytics datasource plugin
RUN grafana-cli plugins install blackcowmoo-googleanalytics-datasource

# Set default configuration
ENV GF_SECURITY_ADMIN_USER=admin
ENV GF_SECURITY_ADMIN_PASSWORD=admin
ENV GF_INSTALL_PLUGINS=grafana-sentry-datasource,blackcowmoo-googleanalytics-datasource

EXPOSE 3000
