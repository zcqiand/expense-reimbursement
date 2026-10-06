FROM python:3.11-slim

WORKDIR /app

COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir -e .

ENV AGENT_PORT=8100
EXPOSE 8100

CMD ["python", "-m", "expense_agent"]
