const result = document.querySelector("#demoResult");
const button = document.querySelector("#demoButton");
const year = document.querySelector("#year");

button.addEventListener("click", () => {
  result.textContent = "JavaScript is working. Your static project is ready.";
  button.textContent = "Interaction tested";
});

year.textContent = new Date().getFullYear();
