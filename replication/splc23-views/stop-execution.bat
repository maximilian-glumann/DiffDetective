@echo "Stopping all running simulations. This will take a moment..."
@FOR /f "tokens=*" %%i IN ('docker ps -a -q --filter "ancestor=diff-detective-views"') DO docker stop %%i
@echo "...done."