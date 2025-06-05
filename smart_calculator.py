import ast
import operator
import math


class SafeEval(ast.NodeVisitor):
    ALLOWED_OPERATORS = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.Mod: operator.mod,
        ast.Pow: operator.pow,
    }

    def __init__(self, variables):
        self.variables = variables

    def visit_BinOp(self, node):
        left = self.visit(node.left)
        right = self.visit(node.right)
        op_type = type(node.op)
        if op_type in self.ALLOWED_OPERATORS:
            return self.ALLOWED_OPERATORS[op_type](left, right)
        raise ValueError(f"Operator {op_type} not allowed")

    def visit_UnaryOp(self, node):
        operand = self.visit(node.operand)
        if isinstance(node.op, ast.UAdd):
            return +operand
        if isinstance(node.op, ast.USub):
            return -operand
        raise ValueError(f"Unary operator {type(node.op)} not allowed")

    def visit_Call(self, node):
        if not isinstance(node.func, ast.Name):
            raise ValueError("Only simple function calls are allowed")
        func_name = node.func.id
        if func_name in math.__dict__:
            func = getattr(math, func_name)
        else:
            raise ValueError(f"Function {func_name} not allowed")
        args = [self.visit(arg) for arg in node.args]
        return func(*args)

    def visit_Name(self, node):
        if node.id in self.variables:
            return self.variables[node.id]
        if node.id in math.__dict__:
            return getattr(math, node.id)
        raise ValueError(f"Unknown variable {node.id}")

    def visit_Expr(self, node):
        return self.visit(node.value)

    def visit_Constant(self, node):
        return node.value

    def generic_visit(self, node):
        raise ValueError(f"Unsupported syntax {type(node)}")


def eval_expr(expr, variables):
    tree = ast.parse(expr, mode="eval")
    evaluator = SafeEval(variables)
    return evaluator.visit(tree.body)


def repl():
    vars = {"ans": 0}
    print("Smart Calculator. Type 'exit' to quit.")
    while True:
        try:
            expr = input(">>> ")
        except EOFError:
            break
        if expr.strip().lower() in {"exit", "quit"}:
            break
        if not expr.strip():
            continue
        try:
            result = eval_expr(expr, vars)
            print(result)
            vars["ans"] = result
        except Exception as exc:
            print("Error:", exc)


if __name__ == "__main__":
    repl()
