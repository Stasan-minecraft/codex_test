class LombikAI:
    """Simple rule-based AI named LOMBIK AI."""
    def __init__(self, name="LOMBIK AI"):
        self.name = name

    def respond(self, message: str) -> str:
        """Generate a simple response based on keywords."""
        message = message.lower()
        if "hello" in message or "hi" in message:
            return f"Hello! I am {self.name}. How can I assist you today?"
        if "your name" in message:
            return f"I am {self.name}."
        if "help" in message:
            return "Sure, I'm here to help. What do you need?"
        return "I'm not sure how to respond to that, but I'm learning!"


def chat():
    """Run a simple chat session with Lombik AI."""
    ai = LombikAI()
    print(f"Welcome to {ai.name}! Type 'quit' to exit.")
    while True:
        user_input = input("You: ")
        if user_input.strip().lower() == 'quit':
            print("Goodbye!")
            break
        response = ai.respond(user_input)
        print(f"{ai.name}: {response}")


if __name__ == "__main__":
    chat()
