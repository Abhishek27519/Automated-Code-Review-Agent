# Sample inputs

Deliberately flawed code used to demo the review agent. **Never use this code in production.**

| File | Planted flaw |
|---|---|
| SqlInjectionDemo.java | SQL injection through string concatenation (lines 17 and 30) |
| ResourceLeakDemo.java | `BufferedReader` is never closed (line 11) |
| quadratic_loop.py | Nested loop plus `in` list scan, O(n^2) or worse (lines 3-6) |